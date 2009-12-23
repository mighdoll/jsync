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
import com.digiting.util.HandyLog
import TryCast.tryCast

/** this should replace AppService below */
class AppService3[T <: Syncable](val serviceName:String, debugId:String, messageClass:Class[T],
                                 val queue:SyncableSeq[T], handler:(T)=>Unit) extends HandyLog {
  val logging = "AppService." + serviceName
  
  log.trace("init()")
  Observers.watch(queue, queueChanged, serviceName)
  
  private def queueChanged(change:ChangeDescription) {
    log.trace("queueChanged(): %s", change)
    for {
      insertAt <- AsInsertAtChange.unapply(change) orElse 
        error("unexpected change %s in queue", change.toString)
      (queue, messageObj, at) <- InsertAtChange.unapply(insertAt) orElse
        error("can't unapply InsertAtChange !?", insertAt.toString)
      message <- tryCast(messageObj, messageClass) orElse 
        error("unexpected type of message received: ", messageObj.toString)        
    } { 
      handler(message)
    }
  }
}


object AsInsertAtChange {
  def unapply(ref:AnyRef):Option[InsertAtChange] = {
    ref match {
      case insertAt:InsertAtChange => Some(insertAt)
      case _ => None
    }
  }
}


/** An AppService publishes a message queue that the client into which the client can drop
 * syncable objects.  Subclasses of AppService are responsible for handling the received objects
 * appropriately.
 */
trait AppService extends HasTransientPartition with HandyLog {
  type MessageHandler = PartialFunction[AnyRef, Unit]
  
  val handleMessage:MessageHandler		// set by message handler
  val serviceName:String    			// set by subTrait 
  val app:AppContext					// set by instantiator
  
  lazy val logging = serviceName
  lazy val transientPartition = app.transientPartition  
  lazy val clientQueue = createMessageQueue()
  
  private def createMessageQueue():SyncableSeq[Syncable] = {        
    val messageQueue = 
      withTransientPartition {
        new SyncableSeq[Syncable]  // LATER make this a server-dropbox, client doesn't need to save messages after they're sent
      }
    
    Observers.watch(messageQueue, queueChanged, serviceName)
    messageQueue
  }
  
  object AsInsertAtChange {
    def unapply(ref:AnyRef):Option[InsertAtChange] = {
      ref match {
        case insertAt:InsertAtChange => Some(insertAt)
        case _ => None
      }
    }
  }
  
  private def queueChanged(change:ChangeDescription) {
    for {
      insertAt <- AsInsertAtChange.unapply(change) orElse 
        error("unexpected change %s in queue", change.toString)
      (queue, messageObj, at) <- InsertAtChange.unapply(insertAt) orElse
        error("can't unapply InsertAtChange !?", insertAt.toString)
      message = messageObj.asInstanceOf[AnyRef]} {
            
        if (handleMessage.isDefinedAt(message)) {
          handleMessage(message)
        } else {
           log.error("unexptected login message received: %s  for service: %s in app %s", 
             message, serviceName, app.connection.debugId)
        }        
      }
  }
}

/*
 * A thought about what the full url for a service queue might be when we expose
 * services via a REST interface.
 * 
 * http://hostname/sync-rest/guestUserApp/.transient/.services/services/loginService
 * 
 * http://hostname/sync-rest   a get/post http/json interface
 * /guestUserApp               the application context 
 * /.transient				   the partition
 * /.services			       the id of a Services object (which magically exposes services as fields, like a .js hash)
 * /loginService			   the field loginService - a queue into which to drop messages
 * 
 * The simpler version of REST access to an individual object:
 * 
 * http://hostname/sync-rest/fred/4891
 * /fred					   the partition (for user Fred)
 * 4891						   the id for this object
 * 
 * and REST access to fields on this object.   Imagine it's a folder={folderName:"", contents:[folder1,folder2]}
 * 
 * http://hostname/sync-rest/fred/4891/folderName
 * http://hostname/sync-rest/fred/4891/contents/0
*/
