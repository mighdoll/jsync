/*   Copyright [2009] Digiting Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.digiting.sync
import net.lag.logging.Logger
import scala.util.DynamicVariable

/** global access to the current application, not currently used */
object App {
  val current = new DynamicVariable[Option[AppContext]](None)
  def currentAppName:String = current.value match {
    case Some(app) => app.appName
    case _ => "<no-current-app>"
  }
}

trait HasTransientPartition {
  val transientPartition:Partition

  def withTransientPartition[T] (fn: =>T):T = {
    SyncManager.currentPartition.withValue(transientPartition) {
      fn
    }
  }
}


// CONSIDER -- the apps should probably be actors..
// for now, we assume that each app context has one and only one connection
class AppContext(val connection:Connection) extends HasTransientPartition {
  private val log = Logger("AppContext")
  override val transientPartition = new RamPartition(connection.connectionId)
  val appName = "server-application"
  val processMessage = new ProcessMessage(this)  
  var implicitPartition = new RamPartition(".implicit-"+ connection.connectionId) // objects known to be on both sides
  val defaultPartition = new RamPartition("user") 		// we'll create new client objects here, TODO make this a per user persistent partition
  val subscriptionService = new {val app = this} with SubscriptionService
  val responses = new ResponseManager(connection.takeSendBuffer)
  
  // when we commit, send changes to the client too
  watchCommit(sendPendingChanges)
      
  def commit() {
	SyncManager.instanceCache.commit();
  }
      
  /** LATER make this use STM, but for now just feed through to the sync manager */
  def watchCommit(func:(Seq[ChangeDescription])=>Unit) {
  	SyncManager.instanceCache.watchCommit(func)
  }
    
  /** send any queued model changes to the client in a single transaction 
   * 
   * (Note that this may be called from an arbitrary thread)
   */
  private def sendPendingChanges(ignored:Seq[ChangeDescription]) = {
    val pending = subscriptionService.active.takePending()
    if (!pending.isEmpty) {
      var message = Message.makeMessage(pending)
      log.trace("sendPendingChanges #%s: queueing Pending Change: %s", connection.debugId, message.toJson)
      connection.putSendBuffer ! PutSendBuffer.Put(message)
    } else {
      log.trace("sendPendingChanges #%s: nothing Pending", connection.debugId)
    }
  }
  
  /** Run the provided function in the context of this application */
  def withApp[T](fn: =>T):T = {
    App.current.withValue(Some(this)) {
      Observers.currentMutator.withValue(appName) {
        val result = fn 
        commit()  // commit changes to partitions and to subscribing clients
        result
      }
    }      
  }
  
  def createImplicitService[T <: Syncable](serviceName:String, messageClass:Class[T], 
                                           fn:(T)=>Unit):AppService3[T] = {
    log.info("createImplicitService: %s(%s)", serviceName, messageClass.getName)
	val ids = new SyncableIdentity(serviceName, implicitPartition)
    val messageQueue = SyncManager.setNextId.withValue(ids) {
      new SyncableSeq[T]  // LATER make this a server-dropbox, client/server don't need to save messages after they're sent
    }
    
    new AppService3(serviceName, connection.connectionId, messageClass, messageQueue, fn)
  }

  
  import java.lang.reflect.Method
  import com.digiting.sync.syncable.ServiceCall
  
  def createImplicitServices(services:AnyRef) {
    for {method <- services.getClass().getDeclaredMethods } {

      log.trace(method.getName + "() annotations:" + method.getAnnotations.toList.mkString(", "))
      val methodAnnotation = method.getAnnotation(classOf[ImplicitService])
      method.getName match {
        case methodName if methodName.contains('$') =>
          log.trace("ignoring compiler generated method: " + methodSignature(method))          
        case methodName if methodAnnotation == null =>
          log.trace("ignoring method that isn't annotated as ImplicitService: " + methodSignature(method))                    
        case methodName if (method.getReturnType == java.lang.Void.TYPE || 
                      method.getReturnType == classOf[Syncable]) =>
          val classAnnotation = services.getClass.getAnnotation(classOf[ImplicitServiceClass])          
          val className = 
            if (classAnnotation != null && classAnnotation.value() != "") {
              classAnnotation.value()
            } else {
              services.getClass.getName
            } 
          val serviceName = className + "." + methodName
          createImplicitService(serviceName, classOf[ServiceCall], serviceCall(method, services))
        case _ =>
          log.error("createImplicitServices: can't create service for method signature: " + methodSignature(method))
          throw new NotYetImplemented("createImplicitServices: can't create service for method signature: " + methodSignature(method))
      }
    }
  }
  
  /** return a function which accepts a ServiceCall message, calls the service method, and 
    * modifies the ServiceCAll result value to reflect the result */
  private def serviceCall(method:Method, target:AnyRef):(ServiceCall)=>Unit = {
    { serviceCall:ServiceCall =>
      val args = serviceCall.arguments(method).toArray;
      val results = method.invoke(target, args: _*) 
      serviceCall.results = results.asInstanceOf[Syncable]
    }
  }
  
  private def methodSignature(method:java.lang.reflect.Method):String = {
    String.format("%s(%s):%s", method.getName, 
      method.getParameterTypes map {_.getName} mkString(","), 
      method.getReturnType.getName)
  }
}
