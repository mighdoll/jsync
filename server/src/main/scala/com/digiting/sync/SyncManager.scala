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
import net.lag.logging.Logger

/**
 * Some handy interfaces for creating and fetching syncable objects
 * 
 * SOON some more cleanup here, it's kind of a grab bag of functionality
 */
object SyncManager {
  type Kind = String	// buys a little documentation benefit.. CONSIDER conversion to case class?
  
  case class VersionedKind (val kind:Kind, val version:String)
  val log = Logger("SyncManager")
  
  // global instance cache.  SOON this should be per-connection
  val instanceCache = new InstancePool("SyncManager")
  
  // prebuilt reflection tools, one for each $kind of Syncable
  val metaAccessors = mutable.Map.empty[Kind, ClassAccessor]
  
  // prebuilt reflection tools, one for each $kind of old Syncable (Migration)
  val migrations = mutable.Map.empty[VersionedKind, ClassAccessor]

  // to force the id of the next created object (to instantiate remotely created objects)
  val setNextId = new Takeable[SyncableIdentity]
  
  // default partition for new objects
  val currentPartition = new DynamicVariable[Partition](null)	

  // don't register a creation change while this is true (so partitions can instantiate objects)
  val quietCreate = new DynamicVariable[Boolean](false)
  
  // true while we're creating an ephemeral syncable, that isn't mapped in the index or observed
  private var creatingFake = new DynamicVariable[Boolean](false);  
  
  // dummy partition for fake objects
  private val fakePartition = new FakePartition("fake")
  
  reset()

  // watch for changes, and commit them to the partitions.  This should be per-connection
  instanceCache.watchCommit(commitToPartitions)

  /** write pending changes to persistent storage */
  private def commitToPartitions(changes:Seq[ChangeDescription]) {
    for (change <- changes) {
      change.target.asInstanceOf[Syncable].partition.update(change)
    }
  }
  
  /** For testing, reset as if we'd just rebooted.  */
  def reset() {
    currentPartition.value = null
    metaAccessors.clear
    registerSyncableKinds()
    setNextId.set(None)
    TestData.setup()  // SOON, move this to test application
  }
  
  /** untested.  create a local JsonMap for*/
  private def constructLocalMapForSyncable(kind:Kind, ids:SyncableIdentity):Option[SyncableJson] = {
    // handle case we're we don't have a server class registered 
    setNextId.withValue(ids) {
      val js = new SyncableJson()
      js.kind = kind
      Some(js)
    }        
  }
  
  /** construct a new syncable instance in the specified partition with the specified class*/
  def newSyncable(kind:Kind, ids:SyncableIdentity):Option[Syncable] = {
    metaAccessors.get(kind) match {
      case Some(meta) => 
        constructSyncable(meta.theClass.asInstanceOf[Class[Syncable]], ids)
      case None =>
        log.error("no server class found for kind: " + kind)
        constructLocalMapForSyncable(kind, ids)  // not currently tested or used
    }
  }
    
  /** construct a new blank syncable instance.
   * does not send a creation notification to observers.
   * if the requested kindVersion is obsolete (due to Migration), then return the migrated-to type
   */
  def newBlankSyncable(kind:Kind, kindVersion:String, ids:SyncableIdentity):Option[Syncable] = {
    quietCreate.withValue(true) {
      migrations get VersionedKind(kind, kindVersion) match {
        case Some(meta) =>
          constructSyncable(meta.theClass.asInstanceOf[Class[Syncable]], ids)
        case _ =>  
          newSyncable(kind,ids)
      }
    }
  }

  /** reflection access to this kind of syncable */
  def propertyAccessor(syncable:Syncable):ClassAccessor = {
    propertyAccessor(syncable.kind)
  }
  
  /** reflection access to this kind of syncable */
  def propertyAccessor(kind:Kind):ClassAccessor = {
    metaAccessors.get(kind) getOrElse {
      log.error("accessor not found for kind: %s", kind)
      throw new ImplementationError
    }
  }

  
  /** retrieve an object synchronously an arbitrary partition.  Stores the object in the local
    * instance cache.  */
  def get(partitionId:String, syncableId:String):Option[Syncable] = {    
    instanceCache get(partitionId, syncableId) orElse {
      val foundOpt = Partitions.get(partitionId) match {
        case Some(partition) => partition.get(syncableId) 
        case _ =>
          log.error("unexpected partition in Syncables.get: " + partitionId)
          None        
      }
      foundOpt map (instanceCache put _)	
      foundOpt
	}   
  }
  
  /** retrieve an object synchronously an arbitrary partition.  Stores the object in the local
    * instance cache.  */
  def get(ids:SyncableIdentity):Option[Syncable] = {
    instanceCache get(ids.partition.partitionId, ids.instanceId) orElse {
      ids.partition get ids.instanceId map {found =>
        instanceCache put found
        found
      }
    }
  }

  /** build a syncable with a specified Id */
  private def constructSyncable(clazz:Class[Syncable], ids:SyncableIdentity):Option[Syncable] = {
    setNextId.withValue(ids) {
      Some(clazz.newInstance)
    }
  }
  
  /** register kind to class mapping, so we can receive and instantiate objects of this kind */
  def registerKind(clazz:Class[_ <: Syncable]) = {
    withFakeObject {  
      val syncable:Syncable = clazz.newInstance  // make a fake object to read the kind field
      val accessor = SyncableAccessor.get(clazz)
      val kind = syncable.kind
      syncable match {
        case migration:Migration[_] =>
          migrations += (VersionedKind(kind, migration.kindVersion) -> accessor)
        case _ =>
          metaAccessors += (kind -> accessor)          
      }
    }
  }
  
  /** register kind to class mappings, so we can receive and instantiate objects of those kinds
    * uses reflection to find all classes in the same package as the provided class */
  def registerKindsInPackage(clazz:Class[_ <: Syncable]) {
    val classes = ClassReflection.collectClasses(clazz, classOf[Syncable])
    classes foreach {syncClass =>
      if (!syncClass.isInterface) {
        log.trace("registering class %s", syncClass.getName)
        registerKind(syncClass)
      }
    }
  }
  
  /** run a function with the current partition temporarily set.  Handy for creating objects
    * in a specified partition */
  def withPartition[T](partition:Partition)(fn: =>T):T = {
    currentPartition.withValue(partition) {
      fn
    }      
  }
  
  /** handy routine for making a temporary object, that will not be saved in a persistent partition */
  def withFakeObject[T](fn: => T):T = {
    try {
      creatingFake.value = true
      setNextId.withValue(SyncableIdentity("fake", fakePartition)) {
        fn
      }
    } finally {
      creatingFake.value = false;
    }
  }
  
  private def registerSyncableKinds() {
    /* one class from each package, package search finds the rest */
    SyncManager.registerKindsInPackage(classOf[Subscription])    
    SyncManager.registerKindsInPackage(classOf[TestNameObj])
    SyncManager.registerKindsInPackage(classOf[SyncableSet[_]])
  }
  
  /** copy a set of properities to a target syncable instance */
  def copyFields(srcFields:Iterable[(String, AnyRef)], target:Syncable) = {    
    val classAccessor = SyncableAccessor.get(target.getClass)
    for ((propName, value) <- srcFields) {
      classAccessor.set(target, propName, value)
    }
  }
     
  /** create the identity for a new object */
  def creating(syncable:Syncable):SyncableIdentity = {    
	val id = setNextId.take() getOrElse SyncableIdentity(newSyncableId(), currentPartition.value)
    log.trace("creating(): %s", id)
    id
  }
  
  
  /** called when a new instance is created */
  def created(syncable:Syncable) {
    log.trace("created(): %s", syncable)
    assert(syncable.partition != null)
    if (!creatingFake.value) {
      instanceCache put syncable
      if (!quietCreate.value)
        instanceCache created syncable
    }
  }
  
  /** create a unique id for a new syncable */
  private def newSyncableId():String = {
    "s_" + RandomIds.randomUriString(32);
  }  
  
}

case class SyncableIdentity(val instanceId:String, val partition:Partition) {
  override def toString():String = instanceId + "/" + partition.partitionId
  
  /** LATER change partition to a partitionId string */
}