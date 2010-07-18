package com.digiting.sync
import collection.mutable
import com.digiting.util._
import SyncManager.withFakeObject
import Log2._

object SyncableKinds {
  type Kind = String  
}

import SyncableKinds.Kind

case class VersionedKind (val kind:Kind, val version:String)

class SyncableKinds {
  implicit private val log = logger("SyncableKinds")
  
  // reflection access for each $kind of Syncable
  val kinds = mutable.Map.empty[Kind, ClassAccessor]
  
  // reflection access for each old $kind of Syncable (MigrateTo)
  val migrators = mutable.Map.empty[VersionedKind, ClassAccessor]

  registerSyncableKinds()
  
  def accessor(kindedId:KindVersionedId):ClassAccessor = {
	  migrators get VersionedKind(kindedId.kind, kindedId.kindVersion) getOrElse {
      accessor(kindedId.kind) 
    } 
  } 

  def accessor(kind:Kind):ClassAccessor = {
    kinds get kind getOrElse {
      abort2("can't find accessor for kind: %s", kind)          
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
          migrators += (VersionedKind(kind, migration.kindVersion) -> accessor)
        case _ =>
          kinds += (kind -> accessor)          
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
    
  /** reflection access to this kind of syncable */
  def propertyAccessor(syncable:Syncable):ClassAccessor = {
    propertyAccessor(syncable.kind)
  }
  
  /** reflection access to this kind of syncable */
  def propertyAccessor(kind:Kind):ClassAccessor = {
    kinds.get(kind) getOrElse {
      log.error("accessor not found for kind: %s", kind)
      throw new ImplementationError
    }
  }

    
  private def registerSyncableKinds() {
    import com.digiting.sync.syncable.TestNameObj
    import com.digiting.sync.syncable.Subscription
    /* one class from each package, package search finds the rest */
    registerKindsInPackage(classOf[Subscription])    
    registerKindsInPackage(classOf[TestNameObj])
    registerKindsInPackage(classOf[SyncableSet[_]])
  }


}
