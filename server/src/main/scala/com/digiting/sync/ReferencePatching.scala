package com.digiting.sync

import com.digiting.util.LogHelper
import scala.util.DynamicVariable
import collection.mutable.ListBuffer
import JsonMapParser.Reference

case class ReferencePatch(referer:Syncable, field:String, targetId:SyncableId) {
  override def toString = String.format("RefPatch %s.%s = %s", referer, field, targetId)
}

/** Collect inter-object references while decoding an object.  This is useful while
 * parsing e.g. a protocol message, when the target of reference may not be instantiated yet.
 */
object ReferencePatching extends LogHelper {
  val log = logger("ReferencePatching")
  
  val references = new DynamicVariable[Option[ListBuffer[ReferencePatch]]](None)
  
  def collectReferences(fn: =>Unit):Seq[ReferencePatch] = {
    val patches = new ListBuffer[ReferencePatch]
    references.withValue(Some(patches)) {
      fn
    }
    patches
  }
  
  def addReference(referer:Syncable, field:String, target:SyncableId) {    
    val patch = ReferencePatch(referer,field,target)    
    log.trace("adding reference: %s", patch)
    references.value map {_ += patch} orElse
      err("Reference() used outside of collectReferences()")
  }
  
  def valueFromJson(syncable:Syncable, field:String, value:Any):Any = {
    value match {
      case Reference(ids) => 
        addReference(syncable, field, ids)
        null  // set value to null for now, we'll patch references after all objects loaded
      case PrimitiveJsonValue(primitiveObj) => primitiveObj
      case _ => 
        log.error("received unexpected value type in JSON: " + value); null
        null
    }
  }

}
