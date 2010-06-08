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

import scala.collection._
import com.digiting.sync.aspects.Observable
import com.digiting.sync.syncable._
import com.digiting.util._
import scala.util.DynamicVariable
import net.lag.logging.Logger
import scala.collection.mutable.Buffer

/**
 * Some handy interfaces for creating and fetching syncable objects
 * 
 * SOON some more cleanup here, it's kind of a grab bag of functionality
 */
object SyncManager extends LogHelper {
  type Kind = String	// buys a little documentation benefit.. CONSIDER conversion to case class?
  
  case class VersionedKind (val kind:Kind, val version:String)
  case class NextVersion(syncable:Syncable, version:String)
  
  val log = Logger("SyncManager")
  
  // global instance cache.  SOON this should be per-connection
  val instanceCache = new WatchedPool("SyncManager")
  
  // prebuilt reflection tools, one for each $kind of Syncable
  val metaAccessors = mutable.Map.empty[Kind, ClassAccessor]
  
  // prebuilt reflection tools, one for each $kind of old Syncable (Migration)
  val migrations = mutable.Map.empty[VersionedKind, ClassAccessor]

  // to force the id of the next created object (to instantiate remotely created objects)
  val setNextId = new DynamicOnce[SyncableId]
  
  // default partition for new objects
  val currentPartition = new DynamicVariable[Partition](null)	

  // don't register a creation change while this is true (so partitions can instantiate objects)
  val quietCreate = new DynamicVariable[Boolean](false)
  
  // true while we're creating an ephemeral syncable, that isn't mapped in the index or observed 
  private val creatingFake = new DynamicOnce[Boolean];  
  
  // force the version for (untested)
  val setNextVersion = new DynamicOnce[NextVersion]
  
  // dummy partition for fake objects
  private val fakePartition = new FakePartition("fake")
  
  reset()

  // watch for changes, and commit them to the partitions.  This should be per-connection
  instanceCache.watchCommit(commitToPartitions)

  /** write pending changes to persistent storage */
  private def commitToPartitions(changes:Seq[ChangeDescription]) {
    val dataChanges = 
      for {
        change <- changes
        dataChange <- matchDataChange(change)
        partition <- Partitions.get(change.target.partitionId.id) orElse 
          err("partition not found for change: %s", change.toString)
      } yield {
        (dataChange, partition)
      }
    
    // sort changes by partition
    val partitions = new MultiBuffer[Partition, DataChange, mutable.Buffer[DataChange]] 
    dataChanges foreach { case (dataChange, partition) => 
      partitions append(partition, dataChange) 
    }

  
    // transaction boundary within each partition
    partitions foreach { case (partition, dataChanges) =>
      partition.withTransaction {
        dataChanges foreach {change =>
          log.trace("commitTo updating: %s", change)
          partition.update(change)
        }
      }
    }
  }
  
  private def matchDataChange(change:ChangeDescription):Option[DataChange] = {
    change match {
      case dataChange:DataChange => Some(dataChange)
      case _ => None
    }
  }
  
  /** For testing, reset as if we'd just rebooted.  */
  def reset() {
    currentPartition.value = null
    metaAccessors.clear
    registerSyncableKinds()
    setNextId.set(None)
  }
  
  /** untested.  create a local JsonMap for*/
  private def constructLocalMapForSyncable(kind:Kind, ids:SyncableId):Option[SyncableJson] = {
    // handle case we're we don't have a server class registered 
    setNextId.withValue(ids) {
      val js = new SyncableJson()
      js.kind = kind
      Some(js)
    }        
  }
  
  /** construct a new syncable instance in the specified partition with the specified class*/
  def newSyncable[T <: Syncable](kind:Kind, ids:SyncableId):T = {
    metaAccessors.get(kind) match {
      case Some(meta) => 
        constructSyncable(meta.clazz.asInstanceOf[Class[T]], ids)
      case None =>
        log.error("no server class found for kind: " + kind)
        NYI()
    }
  }
    
  /** construct a new blank syncable instance.
   * does not send a creation notification to observers.
   * if the requested kindVersion is obsolete (due to Migration), then return the migrated-to type
   */
  def newBlankSyncable(kind:Kind, kindVersion:String, ids:SyncableId):Syncable = {
    quietCreate.withValue(true) {
      migrations get VersionedKind(kind, kindVersion) match {
        case Some(meta) =>
          constructSyncable(meta.clazz.asInstanceOf[Class[Syncable]], ids)
        case _ =>  
          newSyncable(kind,ids)
      }
    }
  }
  
  def newBlankSyncable[T <: Syncable](kind:Kind, id:SyncableId):T = {
    val ident = SyncableIdentity(id.instanceId, Partitions.getMust(id.partitionId.id))
    quietCreate.withValue(true) {
      newSyncable(kind, id).asInstanceOf[T]
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
    instanceCache get(ids.partition.partitionId.id, ids.instanceId) orElse {
      ids.partition get ids.instanceId map {found =>
        instanceCache put found
        found
      }
    }
  }
  
  /** retrieve an object synchronously an arbitrary partition.  Stores the object in the local
    * instance cache.  */
  def get(ids:SyncableId):Option[Syncable] = {
    instanceCache get(ids.partitionId.id, ids.instanceId) orElse {
      Partitions.get(ids.partitionId.id) orElse {
        err("no partition found for: %s", ids.toString)
      } flatMap {partition =>
        partition get ids.instanceId map {found =>
          instanceCache put found
          found
        }
      }
    }
  }

  /** build a syncable with a specified Id */
  private def constructSyncable[T <: Syncable](clazz:Class[T], ids:SyncableId):T = {
    withNextNewId(ids) {
      clazz.newInstance
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
  
  def withNextNewId[T](id:SyncableId)(fn: =>T):T = {
    setNextId.withValue(id) {
      fn
    }
  }
  
  /** handy routine for making a temporary object, that will not be saved in a persistent partition */
  def withFakeObject[T](fn: => T):T = {
    creatingFake.withValue(true) {
      withNextNewId(SyncableId(fakePartition.partitionId, "fake")) {
        fn
      }
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
      
      val adaptedValue = convertJsType(value, classAccessor.propertyAccessors(propName))
      classAccessor.set(target, propName, adaptedValue)
    }
  }
  
  /** convert javascript doubles to local numeric types */
  private def convertJsType(jsValue:AnyRef, property:PropertyAccessor):AnyRef = {
    if (jsValue.getClass == property.clazz) {
      jsValue
    } else {
      jsValue match {
        case d:java.lang.Double if (property.clazz == classOf[Int]) =>
          java.lang.Integer.valueOf(d.intValue)          
        case d:java.lang.Double if (property.clazz == classOf[Float]) =>
          java.lang.Float.valueOf(d.floatValue)
        case d:java.lang.Double if (property.clazz == classOf[Byte]) =>
          java.lang.Float.valueOf(d.byteValue)
        case x =>
          log.fatal("unmatched value type %s for property %s", jsValue.getClass, property)
          x
      }
    }
    
    // LATER, DRY with SyncableSerialize 
  }
  
  
     
  /** create the identity for a new object */
  def creating(syncable:Syncable):SyncableIdentity = {    
    val identity = setNextId.take() match {
      case Some(id) => 
        SyncableIdentity(id.instanceId, Partitions.getMust(id.partitionId.id))
      case None =>
        SyncableIdentity(newSyncableId(), currentPartition.value)
    }
    log.trace("creating(): %s", identity)
    identity
  }
  
  private[sync] def withGetId[T](id:SyncableId)(fn:(Syncable)=>T):T = {
    SyncManager.get(id) map fn match {
      case Some(result) => result
      case None =>
        err("withGetId can't find: " + id) 
        throw new ImplementationError
    }      
  }
   

  /** called when a new instance is created */
  def created(syncable:Syncable) {
    log.trace("created(): %s", syncable)
    assert(syncable.partition != null)
    creatingFake.take orElse {
      instanceCache put syncable
      if (!quietCreate.value)
        instanceCache created syncable
      None
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
