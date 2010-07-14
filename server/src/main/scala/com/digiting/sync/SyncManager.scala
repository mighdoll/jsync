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
  
  /** construct a new blank syncable instance.
   * does not send a creation notification to observers.
   */
  def newBlankSyncable[T <: Syncable](kind:Kind, id:SyncableId):T = {
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
        case migration:MigrateTo[_] =>
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
      withNextNewId(SyncableId(fakePartition.id, "fake")) {
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
  def copyFields(srcFields:Iterable[(String, Any)], target:Syncable) = {    
    val classAccessor = SyncableAccessor.get(target.getClass)
    for ((propName, value) <- srcFields) {
      
      val adaptedValue = convertJsType(value, classAccessor.propertyAccessors(propName))
      classAccessor.set(target, propName, adaptedValue)
    }
  }
  
  /** convert javascript doubles to local numeric types */
  private def convertJsType(jsValue:Any, property:PropertyAccessor):AnyRef = {
    jsValue match {
      case null => null
      case v:AnyRef if (v.getClass == property.clazz) => v
      case d:java.lang.Double if (property.clazz == classOf[Byte]) =>
        java.lang.Byte.valueOf(d.byteValue)
      case d:java.lang.Double if (property.clazz == classOf[Short]) =>
        java.lang.Short.valueOf(d.shortValue)
      case d:java.lang.Double if (property.clazz == classOf[Int]) =>
        java.lang.Integer.valueOf(d.intValue)          
      case d:java.lang.Double if (property.clazz == classOf[Long]) =>
        java.lang.Long.valueOf(d.longValue)          
      case d:java.lang.Double if (property.clazz == classOf[Float]) =>
        java.lang.Float.valueOf(d.floatValue)
      case d:java.lang.Double if (property.clazz == classOf[Double]) =>
        d
      case d:java.lang.Double if (property.clazz == classOf[String]) =>
        log.warning("autoConverting double to string for %d into field: %s", d, property.name)
        d.toString
      case b:java.lang.Boolean if (property.clazz == classOf[Boolean]) =>
        b
      case s:String if (property.clazz == classOf[Char]) =>
        java.lang.Character.valueOf(s(0))
      case x:AnyRef =>
        log.fatal("unmatched value type %s for property %s:%s", x.getClass, property.name, property.clazz)
        x
    }    
    // LATER, DRY with SyncableSerialize 
  }
  
  
     
  /** create the identity for a new object */
  def creating(syncable:Syncable):SyncableId = {    
    val identity = setNextId.take() match {
      case Some(id) => 
        id
      case None =>
        SyncableId(currentPartition.value.id, randomInstanceId())
    }
    log.trace("creating(): %s", identity)
    identity
  }
  

  /** called when a new instance is created */
  def created(syncable:Syncable) {
    trace("created(): %s", syncable)
    assert(syncable.partition != null)
    creatingFake.take orElse {
      App.current.value map {app =>
      	app.instanceCache put syncable
        if (!quietCreate.value)
          app.instanceCache created syncable  // CONSIDER can this use app.updated() instead?
      } orElse {
        warn("created %s, but no current App", syncable)
      }
      None
    }
  }
  
  /** create a unique id for a new syncable */
  private def randomInstanceId():String = {
    "s_" + RandomIds.randomId(32);
  }  
  
}

