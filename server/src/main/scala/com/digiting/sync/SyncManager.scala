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

import collection._
import com.digiting.sync.aspects.Observable
import com.digiting.sync.syncable._
import com.digiting.util.Takeable
import scala.util.DynamicVariable
import net.liftweb.util.Log

/**
 * Some handy interfaces for creating and fetching syncable objects
 * 
 * SOON some more cleanup here, it's kind of a grab bag of functionality
 */
object SyncManager {
  type Kind = String	// buys a little documentation benefit.. consider conversion to case class?
  case class NewSyncableIdentity(val instanceId:String, val partition:Partition)
  
  var instanceCache = new InstancePool("SyncManager")
  
  // prebuilt reflection tools, one for each $kind of Syncable
  val metaAccessors = mutable.Map.empty[Kind, ClassAccessor]

  // unique id for creating new objects
  var nextUniqueId:Int = 0

  // to force the id of the next created object (to instantiate remotely created objects)
  var setNextId = new Takeable[NewSyncableIdentity]
  
  // default partition for new objects
  var currentPartition = new DynamicVariable[Partition](new RamPartition("default1"))	
  
  reset()

  instanceCache.watchCommit(commitToPartitions)

  /** write pending changes to persistent storage */
  private def commitToPartitions(changes:Seq[ChangeDescription]) {
    for (change <- changes) {
      change match {
        case CreatedChange(created) => 
          val newObj = created.asInstanceOf[Syncable]
          newObj.partition.put(newObj)
        case p:PropertyChange =>
          p.target.asInstanceOf[Syncable].partition.update(p)
        case _ => 
          // Log.info("commitToPartitions skipping change: " + change)
      }
    }
  }
  
  /** For testing, reset as if we'd just rebooted.  */
  def reset() {
    currentPartition.value = new RamPartition("default")
    metaAccessors.clear
    registerSyncableKinds()
    TestData.setup()
    nextUniqueId = 0
    setNextId.set(None)
  }
  
//  def newSyncable(kind:Kind, partitionId:String, instanceId:String):Option[Syncable]= {
//    Partitions.get(partitionId) match {
//      case Some(partition) => 
//      	newSyncableIn(partition, instanceId, kind)
//      case None => 
//        Log.error("newSyncable(): partition not found: " + partitionId)
//        None
//    }      
//  }

  /** untested.  create a local JsonMap for*/
  private def constructLocalMapForSyncable(kind:Kind, ids:NewSyncableIdentity):Option[SyncableJson] = {
    // handle case we're we don't have a server class registered 
    setNextId.withValue(ids) {
      val js = new SyncableJson()
      js.kind = kind
      Some(js)
    }        
  }
  
  /** construct a new syncable instance in the specified partition with the specified class*/
  def newSyncable(kind:Kind, ids:NewSyncableIdentity):Option[Syncable] = {
    metaAccessors.get(kind) match {
      case Some(meta) => 
        constructSyncable(meta.theClass.asInstanceOf[Class[Syncable]], ids.partition, ids.instanceId)
      case None =>
        Log.error("no server class found for kind: " + kind)
        constructLocalMapForSyncable(kind, ids)
    }
  }

  
  /** retrieve an object synchronously an arbitrary partition.  Stores the object in the local
    * instance cache.  */
  def get(partitionId:String, syncableId:String):Option[Syncable] = {    
	instanceCache get(partitionId, syncableId) orElse {
      val foundOpt = Partitions.get(partitionId) match {
        case Some(partition) => partition.get(syncableId) 
        case _ =>
          Log.error("unexpected partition in Syncables.get: " + partitionId)
          None        
      }
      foundOpt map (instanceCache put _)	
      foundOpt
    }
  }
  
  /** retrieve an object synchronously an arbitrary partition.  Stores the object in the local
    * instance cache.  */
  def get(ids:NewSyncableIdentity):Option[Syncable] = {
    instanceCache get(ids.partition.partitionId, ids.instanceId) orElse {
      ids.partition get ids.instanceId map {found =>
        instanceCache put found
        found
      }
    }
  }


  /** build a syncable with a specified Id */
  private def constructSyncable(clazz:Class[Syncable], partition:Partition, instanceId:String):Option[Syncable] = {
    setNextId.withValue(NewSyncableIdentity(instanceId, partition)) {
      Some(clazz.newInstance)
    }
  }
    
  private def registerKind(kind:Kind, clazz:Class[T] forSome {type T <: Syncable}) = {
    metaAccessors + (kind -> SyncableAccessor.get(clazz))
  }
    
  private def registerSyncableKinds() {
    /* manually for now, SOON search packages for all Syncable classes, and register them (use aspectJ search?) */
    SyncManager.registerKind("$sync.subscription", classOf[Subscription])
    SyncManager.registerKind("$sync.set", classOf[SyncableSet[_]])
    SyncManager.registerKind("$sync.test.nameObj", classOf[TestNameObj])
    SyncManager.registerKind("$sync.test.refObj", classOf[TestRefObj])
    SyncManager.registerKind("$sync.test.twoRefsObj", classOf[TestTwoRefsObj])
    SyncManager.registerKind("$sync.server.publishedRoot", classOf[PublishedRoot])
    SyncManager.registerKind("$sync.test.paragraph", classOf[TestParagraph])
  }
  
  /** copy a set of properities to a target syncable instance */
  def copyFields(srcFields:Iterable[(String, AnyRef)], target:Syncable) = {    
    metaAccessors get target.kind match {
      case Some(classAccessor) => 
        for ((propName, value) <- srcFields) {
          classAccessor.set(target, propName, value)
        }
      case None => Log.error("class accessor not found for kind: " + target.kind)
    }
  }
     
  /** create the identity for a new object */
  def creating(syncable:Syncable):NewSyncableIdentity = {
	setNextId.take() getOrElse NewSyncableIdentity(newSyncableId(), currentPartition.value)
  }
  
  
  /** called when a new instance is created */
  def created(syncable:Syncable) {
    instanceCache put syncable
    // TODO put some logic here to not call created if the object was fetched from a partition
    instanceCache created syncable
  }
  
  /** create a unique id for a new syncable */
  private def newSyncableId():String = {
    // LATER this needs to be unique per partition
    synchronized {
    val id = "server-" + nextUniqueId
	  nextUniqueId += 1
      id
    }
  }  
}


