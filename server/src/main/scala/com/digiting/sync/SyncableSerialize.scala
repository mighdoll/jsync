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
import com.digiting.util._
import net.lag.logging.Logger
import scala.collection.mutable.ListBuffer
import SyncableInfo.isReserved

/** utilities to serialize/deserialize a syncable to from a map of key,value strings */
object SyncableSerialize extends LogHelper {
  case class Reference(val partition:String, id:String, kind:SyncManager.Kind)
  val log = Logger("SyncableSerialize")
  
  def syncableAttributes(syncable:Syncable):Map[String, String] = {
    val props = new ListBuffer[(String,String)]
    for {
        (prop, value) <- SyncableAccessor.properties(syncable) if !isReserved(prop)
        valueString = encodeProperty(value)
      } {
        props += (prop -> valueString) 
      } 
    
    props += "kind" -> syncable.kind    
    props += "kindVersion" -> syncable.kindVersion
    
    Map[String,String]() ++ props
  }
  
  def createFromAttributes(instanceId:String, partition:Partition, 
    attributes:Map[String,String]):Option[Syncable] = {
    
    val syncable:Syncable = null
    for {
      kind <- attributes get "kind" orElse
        err("createFromAttributes() no kind found for atributes %s", attributes.toString)
      kindVersion <- attributes get "kindVersion" orElse Some("0")    
      ids = SyncableId(partition.partitionId, instanceId)      
      syncable = SyncManager.newBlankSyncable(kind, kindVersion, ids)  
    } yield {
      Observers.withNoNotice {
        applyAttributes(syncable, attributes)
      }
      log.trace("createFromAttributes() created: %s", syncable)
      syncable       
    }    
  }

  
  def encodeProperty(value:Any):String = {
    value match {
      case bool:Boolean => bool.toString
      case byte:Byte => byte.toString
      case short:Short => short.toString
      case int:Integer => int.toString
      case long:Long => long.toString
      case char:Char => char.toString
      case string:String => string
      case float:Float => float.toString
      case double:Double => double.toString
      case ref:Syncable => ReferenceString(ref)	  
      case null => "0"
      case _ => 
        throw new ImplementationError("encodeProperty type of value not supported: " + value)
    }
  }

  /** Uses type information from the target syncable to apply an 
   * appropriate parser for each attribute */
  private def applyAttributes(syncable:Syncable, attributes:Map[String,String]) {
   val classAccess = SyncableAccessor.get(syncable.getClass)
    val props = 
      for {
        (prop, valueString) <- attributes  if !isReserved(prop)
        propAccess <- classAccess.propertyAccessors get prop orElse 
          err("applyAttributes:  property %s not found for kind: %s  read data: %s",
                prop, syncable.kind, valueString)
        value = parsePropertyValue(propAccess, valueString)
      } yield
        (prop, value)   
      
    SyncManager.copyFields(props, syncable)     
  }
  
  private def parsePropertyValue(access:PropertyAccessor, valueString:String):AnyRef = {
    val propClass:java.lang.Class[_] = access.propertyClass 
    val boolClass = classOf[Boolean]
    val byteClass = classOf[Byte]
    val shortClass = classOf[Short]
    val intClass = classOf[Int]
    val longClass = classOf[Long]
    val charClass = classOf[Char]
    val stringClass = classOf[String]
    val floatClass = classOf[Float]
    val doubleClass = classOf[Double]
    val syncableClass = classOf[Syncable]
    propClass match {
      case c if c == stringClass => valueString
      case c if valueString == "null" => null
      case c if valueString == "" => throw new ImplementationError("unexpected empty attribute")
      case c if c == boolClass => java.lang.Boolean.valueOf(valueString)
      case c if c == byteClass => java.lang.Byte.valueOf(valueString)
      case c if c == shortClass => java.lang.Short.valueOf(valueString)
      case c if c == intClass => java.lang.Integer.valueOf(valueString)
      case c if c == longClass => java.lang.Long.valueOf(valueString)
      case c if c == charClass => 
        if (valueString.length == 1)
      	  java.lang.Character.valueOf(valueString.charAt(0))
        else 
           throw new ImplementationError("unexpected char value: "+ valueString)
      case c if c == floatClass => java.lang.Float.valueOf(valueString)
      case c if c == doubleClass => java.lang.Double.valueOf(valueString)
      case c if syncableClass.isAssignableFrom(c) => extractAndLoadReference(valueString)
      case _ => throw new ImplementationError("parsePropertyValue can't handle type: " + propClass.getName)
    }
  }
  
  object ReferenceString {
    val matching = "(.*)/(.*)=(.*)".r
    def unapply(refString:String):Option[Reference] = {
      matching.unapplySeq(refString) match {
        case Some(partition::id::kind::Nil) => Some(Reference(partition, id, kind))
        case _ => None
      }
    }
    
    def apply(ref:Syncable):String = {      
      ref.compositeId + "=" + ref.kind
    }
  }
  
  private def extractAndLoadReference(refString:String):Syncable = {
    refString match {
      case ReferenceString(ref) => 
        val loaded = getOrLoad(ref)	// for now we recursively load referenced objects.  LATER, lazy load
        log.trace("extractAndLoadReference: %s", loaded)
        loaded
      case "0" => null
      case _ => 
        log.error("extractAndLoadReference() expected reference: %s", refString)
        throw new ImplementationError
    }
  }
  
  private def getOrLoad(ref:Reference):Syncable = {
    SyncManager.get(ref.partition, ref.id) getOrElse {
      log.error("getOrLoad can't find reference: %s", ref)
      null
    }
  }

}