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
import JsonUtil._
import net.lag.logging.Logger
import com.digiting.util.LogHelper
import com.digiting.sync.JsonObject.JsonMap
import com.digiting.util.TryCast.matchOptString


/** Routines for parsing the json jsync protocol out of JsonMap objects */
object JsonMapParser {
  
  object EditRemoveAt {
    def unapply(edit:JsonMap):Option[Int] = {
      for {
        remove <- edit get "removeAt"
          at <- doubleToIntOpt(remove)
      } yield at     
    }
  }
  
  object EditMove extends LogHelper {
    val log = Logger("EditMove")
    
    def unapply(edit:JsonMap):Option[(Int, Int)] = {
      for {
        moveParam <- edit get "move" orElse
          err("not error, but not move!")
        moveObj <- toJsonMap(moveParam) orElse
          err("can't cast to JsonMap")
        fromDexDouble <- moveObj get "from" orElse
          err("can't find 'from'")
        fromDex <- doubleToIntOpt(fromDexDouble) orElse
          err("can't cast to Int")
        toDexDouble <- moveObj get "to" orElse
          err("can't find 'to'")
        toDex <- doubleToIntOpt(toDexDouble) orElse
          err("can't cast to Int")
      } yield (fromDex, toDex)       
    }
  }
  
  object EditPut {
    def unapply(edit:JsonMap):Option[Any] = {
      edit get "put"
    }
  }
  
  object EditClear {
    def unapply(edit:JsonMap):Option[Boolean] = {
      edit get "clear" match {
        case Some(true) => Some(true)
        case Some(_) => 
          log.error("Unexpected clear value in map: %s", edit)
          None
        case _ => 
          None
      }
    }
  }
  
  
  object EditInsertAt extends LogHelper {
    val log = Logger("EditInsertAt")
    def unapply(edit:JsonMap):Option[List[(Syncable,Int)]]  = {  
      // using two for loops, improve this SCALA?
      
      val insertsOpt = for {
        insertsAny <- edit get "insertAt"
        inserts:List[JsonMap] <- anyToListJsonMapOpt(insertsAny) orElse
          err("no list found")
      } yield {
        inserts 
      } 
      
      insertsOpt map { inserts => 
        for {
          insertMap <- inserts 
          at <- insertMap get "at" orElse
            err("at not found")          
          atInt <- doubleToIntOpt(at) orElse
            err("double not converted")          
          elem <- insertMap get "elem" orElse
            err("elem not found")          
          elemMap <- toJsonMap(elem)
          ids <- JsonSyncableIdentity.unapply(elemMap) orElse
            err("ids not found")          
          syncable <- SyncManager.get(ids) orElse
            err("syncable not found")          
        } yield {
          log.trace("insert %s at: %s", syncable, atInt)
          (syncable, atInt)
        }
      }
    }
    
    private def anyToListJsonMapOpt(any:Any):Option[List[JsonMap]] = {
      any match {
        case list:List[_] => Some(list.asInstanceOf[List[JsonMap]])
        case _ => None
      }
    }
    
  }
  
  
  private def doubleToIntOpt(possibleDouble:Any):Option[Int] = {
    possibleDouble match {
      case d:Double => Some(d.intValue)
      case _ => None
    }
  }
  
  private def parseIntOpt(parse:Any):Option[Int] = {
    parse match {
      case s:String => 
        try {
          Some(s.toInt)
        } catch {
          case e: NumberFormatException => None 
        }
      case _ => None
    }    
  }
  
  
  object JsonSyncableIdentity extends LogHelper {
    val log = Logger("JsonSyncableIdentity")
    
    /** get the instance and partition ids out of map.  If the partitionId isn't
      * specified, use the connections default partition.  
      * 
      * Note: relies on App.current to be valid in order to translate symbolic 
      * partition names like '.implicit'
      */
    def unapply(json:JsonMap):Option[SyncableIdentity] = {
      val foundIds:Option[SyncableIdentity] = 
      matchOptString (json get "id") match { 
        case Some(instanceId) => {
          matchOptString (json get "$partition") match {
            case Some(partitionId) if (partitionId == ".transient") => 
              Some(SyncableIdentity(instanceId, App.current.value.get.transientPartition))
            case Some(partitionId) if (partitionId == ".implicit") => 
              Some(SyncableIdentity(instanceId, App.current.value.get.implicitPartition))
            case Some(partitionId) =>               
              Partitions get partitionId match {
                case Some(partition) => Some(SyncableIdentity(instanceId, partition))
                case None => 
                  err("partition %s not found", partitionId)
              } 
            case None => 
              log.warning("no partition specified in: %s", toJson(json))
              Some(SyncableIdentity(instanceId, App.current.value.get.defaultPartition))
          }         
        }
        case None => err("id not found: %s", json.toString)
      }
        
      log.trace("found: %s", foundIds.toString)
      foundIds orElse {
        err("unable to parse ids in map: %s", json.toString)
      }
    }
  }

  
  private def toStringOpt(possibleString:Any):Option[String] = {
    possibleString match {
      case s:String => Some(s)
      case _ => None
    }
  }
  
  private def toJsonMap(toMap:Any):Option[JsonMap] = {
    toMap match {
      case map:Map[_,_] => Some(map.asInstanceOf[JsonMap])
      case _ => None
    }
  }
  
    /** extract a reference to another syncable from the parsed json results.
    * object references are encoded as $ref objects */
  object Reference extends LogHelper {
    val log = Logger("Reference")
    def unapply(value:Any):Option[SyncableIdentity] = {
      for {
        jsonObj <- toJsonMap(value)
        refVal <- jsonObj get "$ref"
        refObj <- toJsonMap(refVal) 
        ids <- JsonSyncableIdentity.unapply(refObj) orElse
          err("can't parse syncable id from: ", refObj.toString)
      } yield {
        ids
      } 
    }
  }
  
  def valueFromJson(syncable:Syncable, field:String, value:Any):AnyRef = {
    value match {
      case Reference(ids) => 
        ReferencePatches.addReference(syncable, field, ids)
        null  // set value to null for now, we'll patch references after all objects loaded
      case PrimitiveJsonValue(primitiveObj) => primitiveObj
      case _ => 
        log.error("received unexpected value type in JSON: " + value); null
        null
    }
  }
}

import scala.util.DynamicVariable
import collection.mutable.ListBuffer

object ReferencePatches extends LogHelper {
  val log = Logger("ReferencePatches")
  case class ReferencePatch(referer:Syncable, field:String, targetId:SyncableIdentity) {
    override def toString = String.format("RefPatch %s.%s = %s", referer, field, targetId)
  }
  
  val references = new DynamicVariable[Option[ListBuffer[ReferencePatch]]](None)
  
  def collectReferences(fn: =>Unit):Seq[ReferencePatch] = {
    val patches = new ListBuffer[ReferencePatch]
    references.withValue(Some(patches)) {
      fn
    }
    patches
  }
  
  def addReference(referer:Syncable, field:String, target:SyncableIdentity) {    
    val patch = ReferencePatch(referer,field,target)    
    log.trace("adding reference: %s", patch)
    references.value map {_ += patch} orElse
      err("Reference() used outside of collectReferences()")
  }

}
