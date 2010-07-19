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
import SyncableKinds.Kind
import Log2._

/**
 * Some handy interfaces for creating and fetching syncable objects
 * 
 * SOON some more cleanup here, it's kind of a grab bag of functionality
 */
object SyncManager {
  case class NextVersion(syncable:Syncable, version:String)
  
  implicit private val log = logger("SyncManager")
  
  // to force the id of the next created object (to instantiate remotely created objects)
  val setNextId = new DynamicOnce[SyncableId]
  
  // default partition for new objects
  val currentPartition = new DynamicVariable[Partition](null)	
  
  // true while we're creating an ephemeral syncable, that isn't mapped in the index or observed 
  private val creatingFake = new DynamicOnce[Boolean];  
  
  // force the version for (untested)
  val setNextVersion = new DynamicOnce[NextVersion]
  
  // dummy partition for fake objects
  private val fakePartition = new FakePartition("fake")
  
  var kinds:SyncableKinds = null
  
  reset()

  
  /** For testing, reset as if we'd just rebooted.  */
  def reset() {
    currentPartition.value = null
    kinds  = new SyncableKinds()
    setNextId.set(None)
  }
  
  /** construct a new syncable instance in the specified partition with the specified class */
  def newSyncable(kind:Kind, id:SyncableId):Syncable = {
    val access = kinds.accessor(kind)
    constructSyncable(access.clazz.asInstanceOf[Class[Syncable]], id)
  }
  
  /** construct a new syncable instance in the specified partition with the specified class
   * (this variant of newSyncable works for registered obsolete kindVersions too) */
  def newSyncable(kindedId:KindVersionedId):Syncable = {
    val access = kinds.accessor(kindedId)
    constructSyncable(access.clazz.asInstanceOf[Class[Syncable]], kindedId)
  }
  
  /** build a syncable with a specified instance Id */
  private def constructSyncable[T <: Syncable](clazz:Class[T], id:SyncableId):T = {
    withNextNewId(id) {
      clazz.newInstance
    }
  }

  
  /** run a function with the current partition temporarily set.  Handy for creating objects
    * in a specified partition */
  def withPartition[T](partition:Partition)(fn: =>T):T = {
    currentPartition.withValue(partition) {
      fn
    }      
  }

  /** the next Syncable created will have this id */
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
  
  
  /** copy a set of properities to a target syncable instance */
  def copyFields(srcFields:Iterable[(String, Any)], target:Syncable) = {    
    val classAccessor = SyncableAccessor.get(target.getClass)
    for ((propName, value) <- srcFields) {
      trace2("copyFields %s.%s=%s", target, propName, value)
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
  
  
     
  /** create the identity for a new object.  called as a new istance is being initialized */
  def creating(syncable:Syncable):SyncableId = {    
    val identity = setNextId.take() match {
      case Some(id) => 
        id
      case None =>
        SyncableId(currentPartition.value.id, randomInstanceId())
    }
    trace2("creating(): %s", identity)
    identity
  }
  

  /** called when a new instance is created, after the id is assigned */
  def created(syncable:Syncable) {
    trace2("created(): %s", syncable)
    assert(syncable.partition != null)
    creatingFake.take orElse {
      val app = App.app
    	app.instanceCache put syncable
      app.updated(syncable) {
        CreatedChange(SyncableReference(syncable), Pickled(syncable),
        		VersionChange(syncable.version, syncable.version))   
      } 
      None
    }
  }
  
  /** create a unique id for a new syncable */
  private def randomInstanceId():String = {
    "s_" + RandomIds.randomId(32);
  }  
  
}

