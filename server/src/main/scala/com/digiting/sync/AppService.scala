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
import com.digiting.util.LogHelper
import com.digiting.util.TryCast.tryCast

/** An AppService publishes a message queue that the client into which the client can drop
 * syncable objects.  Subclasses of AppService are responsible for handling the received objects
 */
class AppService3[T <: Syncable](val serviceName:String, debugId:String, messageClass:Class[T],
                                 val queue:SyncableSeq[T], handler:(T)=>Unit) extends LogHelper {
  val log = Logger("AppService")
  
  log.trace("init()")
  Observers.watch(queue, queueChanged, serviceName)
  
  private def queueChanged(change:ChangeDescription) {
    log.trace("queueChanged(): %s", change)
    for {
      insertAt <- matchInsertAtChange(change) orElse 
        err("unexpected change %s in queue", change.toString)
      (queueId, messageId, at, versions) <- InsertAtChange.unapply(insertAt) orElse
        err("can't unapply InsertAtChange !?", insertAt.toString)
      messageObj <- SyncManager.get(messageId) orElse 
        err("can't find message target: %s", messageId)
      message <- tryCast(messageObj, messageClass) orElse 
        err("unexpected type of message received: %s", messageObj.toString)        
    } { 
      handler(message)
    }
  }
  
  private def matchInsertAtChange(ref:AnyRef):Option[InsertAtChange] = {
    ref match {
      case insertAt:InsertAtChange => Some(insertAt)
      case _ => None
    }
  }
}



