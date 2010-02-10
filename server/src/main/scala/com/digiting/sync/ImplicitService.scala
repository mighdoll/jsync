package com.digiting.sync
import net.lag.logging.Logger
import com.digiting.util.LogHelper

trait ImplicitServices extends AppContext {
  val log1 = Logger("ImplicitServices")
  import java.lang.reflect.Method
  import com.digiting.sync.syncable.ServiceCall
  
  def createImplicitService[T <: Syncable](serviceName:String, messageClass:Class[T], 
                                           fn:(T)=>Unit):AppService3[T] = {
    log1.trace("createImplicitService: %s(%s)", serviceName, messageClass.getName)
    val ids = new SyncableId(implicitPartition.partitionId, serviceName)
    val messageQueue = SyncManager.withNextNewId(ids) {
      new SyncableSeq[T]  // LATER make this a server-dropbox, client/server don't need to save messages after they're sent
    }
    
    new AppService3(serviceName, connection.connectionId, messageClass, messageQueue, fn)
  }
  
  def createImplicitServices(services:AnyRef) {
    for {method <- services.getClass().getDeclaredMethods } {

      log1.trace(method.getName + "() annotations:" + method.getAnnotations.toList.mkString(", "))
      val methodAnnotation = method.getAnnotation(classOf[ImplicitService])
      method.getName match {
        case methodName if methodName.contains('$') =>
          log1.trace("ignoring compiler generated method: " + methodSignature(method))          
        case methodName if methodAnnotation == null =>
          log1.trace("ignoring method that isn't annotated as ImplicitService: " + methodSignature(method))                    
        case methodName if (method.getReturnType == java.lang.Void.TYPE || 
                      method.getReturnType == classOf[Syncable]) =>
          val classAnnotation = services.getClass.getAnnotation(classOf[ImplicitServiceClass])          
          val className = 
            if (classAnnotation != null && classAnnotation.value() != "") {
              classAnnotation.value()
            } else {
              val rawName = services.getClass.getName
              if (rawName.endsWith("$")) {
                rawName.take(rawName.length - 1)
              } else {
                rawName
              }
            } 
          val serviceName = className + "." + methodName
          createImplicitService(serviceName, classOf[ServiceCall[_]], serviceCall(method, services))
        case _ =>
          log1.error("createImplicitServices: can't create service for method signature: " + methodSignature(method))
          throw new NotYetImplemented("createImplicitServices: can't create service for method signature: " + methodSignature(method))
      }
    }
  }
  
  /** return a function which accepts a ServiceCall message.  The function calls the service 
    * method, and then modifies the ServiceCall result value to reflect the result */
  private def serviceCall(method:Method, target:AnyRef):(ServiceCall[_])=>Unit = {
    (serviceCall:ServiceCall[_]) =>
      val args = serviceCall.arguments(method).toArray;
      val results = method.invoke(target, args: _*) // (may be null, if method returns void)
      subscriptionService.active.withTempRootSubscription(serviceCall) {
        log1.trace("calling service %s", method.getName)
        serviceCall.results = results.asInstanceOf[Syncable]        
      }
  }
  
  private def methodSignature(method:java.lang.reflect.Method):String = {
    String.format("%s(%s):%s", method.getName, 
      method.getParameterTypes map {_.getName} mkString(","), 
      method.getReturnType.getName)
  }
 
}