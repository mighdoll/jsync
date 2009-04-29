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

import JsonObject._
import collection._

object JsonMessageControl extends Enumeration {
	val Init, Continue, Unspecified = Value
}


import JsonMessageControl._

/* protocol message in json object format */
class JsonMessage(val control:JsonMessageControl.Value, var xactNumber: Int, val edits:List[JsonMap], 
                           val syncs:List[JsonMap]) {
  
  def this(edits:List[JsonMap], syncs:List[JsonMap]) = this(Unspecified, -1, edits, syncs)
  
  override def toString = 
    "JsonMessage: " + xactNumber + "  syncs: " + syncs.size + "  edits: " + edits.size 
  
  /* convert a message to a json object string */
  def toJson:String = {
    val metas = new mutable.ListBuffer[JsonMap]
    
    metas append ImmutableJsonMap("#transaction" -> xactNumber)
    
    if (control == Init)
      metas append ImmutableJsonMap("reconnect" -> false)
    else if (control == Continue)
      metas append ImmutableJsonMap("reconnect" -> true)

    val jsons = new mutable.ListBuffer[String]

    appendJsonMaps(jsons, metas.toList, syncs, edits)
    jsons.mkString("[", ",\n", "]\n")
  }
  
  private def appendJsonMaps(jsons:mutable.ListBuffer[String], maps:List[JsonMap]*) {
    for (map <- maps;
         elem <- map) {
      jsons append JsonUtil.toJson(elem, 2)
    }
  }
  
}


case class JsonRef(val targetId:String) { 
  override def toString =
    "$ref:" + targetId
}
