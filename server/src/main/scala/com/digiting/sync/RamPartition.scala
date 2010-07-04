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
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Buffer
import com.digiting.util._
import collection.mutable.HashMap
import collection.mutable.HashSet
import collection.mutable.Set
import scala.collection.mutable.MultiMap
import net.lag.logging.Logger
import com.digiting.util.LogHelper
import java.io.Serializable
import Partition._

/**
 * Simple implemenation of a partition.  
 * 
 * (Note that this implemenation does not respect transactions.)
 */
class RamPartition(partId:String) extends Partition(partId) with LogHelper {
  protected override lazy val log = logger("RamPartition")

  protected val store:collection.mutable.Map[InstanceId,Pickled] = new HashMap[InstanceId, Pickled] 
  
  def commit(tx:Transaction) = true
  
  def get(instanceId:InstanceId, tx:Transaction):Option[Pickled] = synchronized {    
    val result = store get instanceId     
    log.trace("get %s, found: %s", instanceId, result getOrElse "")
    result
  }
  
  def modify(change:DataChange, tx:Transaction) = synchronized {
    val instanceId = change.target.instanceId
    
    change match {
      case created:CreatedChange => 
        put(created.pickled)
      case prop:PropertyChange =>
        get(instanceId, tx) orElse {  
          err("target of property change not found: %s", prop) 
        } foreach {pickled =>
          store(instanceId) = pickled + prop
        }
      case deleted:DeletedChange =>
        throw new NotYetImplemented
      case collectionChange:CollectionChange =>
        store get instanceId orElse {
          abort("collection target not found: %s", change)
        } foreach {pickled =>
          val pickledCollection = pickled.asInstanceOf[PickledCollection]
          store(instanceId) = pickledCollection.revise(collectionChange)
        }
    }
  }
  
  def watch(instanceId:InstanceId, watch:PickledWatch, tx:Transaction) {
    withPickled(instanceId, tx) { pickled =>
      put(pickled + watch)
    }
  }

  def unwatch(instanceId:InstanceId, watch:PickledWatch, tx:Transaction) {
    withPickled(instanceId, tx) { pickled =>
      put(pickled - watch)
    }
  }

  private[this] def put[T <: Syncable](pickled:Pickled) = synchronized {
    trace("put %s ", pickled)
    store += (pickled.reference.instanceId -> pickled)
  }
  
  private def withPickled[T](instanceId:InstanceId, tx:Transaction)(fn:(Pickled)=>T):T = {
    get(instanceId, tx) match {
      case Some(pickled) => 
        fn(pickled)
      case _ =>
        abort("watch() can't find instance %s", instanceId)
    }
  }  
  

  def deleteContents() = synchronized {
    for {(id, pickled) <- store} {
      log.trace("deleting: %s", pickled)
      store -= (id)
    }
  }

  override def debugPrint() {
    for {(_,value) <- store} {
      log.info("  %s", value.toString)
    }
  }
}
