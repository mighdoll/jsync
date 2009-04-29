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

import com.digiting.sync.aspects.Observable

trait Syncable extends Observable {
    def kind:String
    lazy val id:String = sync$presetId getOrElse SyncManager.newSyncableId
    
    /* let's the SyncManager hard code an id, even though we have a parameterless constructor  
     * 
     * There's a flaw in this scheme though.  If the constructor for a subclass
     * references the id before the SyncManager calss setId(), we have a problem
     * and the assert will fire at runtime
     */
    def setId(newId:String) = {
      sync$presetId = Some(newId)
      assert (id == newId)	// trigger's the preset, and makes sure it worked
    }
    var sync$presetId:Option[String] = None
    
    def dotTail(str:String) = {
      val extractTail = ".*\\.(.*)".r
      
      if (str.contains(".")) {
        val extractTail(tail) = str
        tail
      } else
        str
    }
    
    def prettyKind = dotTail(kind) 
        
    override def toString:String = {"[" + id + "," + prettyKind + "]"}
    
    /* Consider  override def hashCode = id.hashCode  */    
}

object SyncableInfo {
  /* true if the field isn't a user settable property (e.g. it's the sync id field) */
  def isReserved(fieldName:String):Boolean = fieldName match {
    case "kind" => true
    case "id" => true
    case "sync$presetId" => true
    case _ => false
  }
}