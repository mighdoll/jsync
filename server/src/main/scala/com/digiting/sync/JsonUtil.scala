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

import org.apache.commons.lang.StringEscapeUtils
import _root_.net.liftweb.common.Empty
import collection._	// CONSIDER importing mutable,immutable explicitly, watch out for wildcards, you might get implicits!
import JsonObject._	// CONSIDER good style on SCALA placement: import types at the top, constants/methods near to the use
import net.lag.logging.Logger
import com.digiting.util._

// CONSIDER making JsonMap it's own class (no explicit conversions, type alias for immutable, someday immutable view of mutable class?, changing in 2.8...)
object JsonObject {
  type JsonMap = Map[String,Any] 
  type ImmutableJsonMap = immutable.HashMap[String, Any]
  type MutableJsonMap = mutable.HashMap[String,Any]
}

object ImmutableJsonMap {
  def apply(elems: (String, Any)*) = mutable.HashMap[String,Any](elems:_*)
}


/** Utility functions to parse and produce json objects.
 * 
 * processJson handles the matching required depending on whether the parsed object is
 * a javascript array or javascript object
 * 
 * 
 */
object JsonUtil {  
  val log = Logger("JsonUtil")
  /** utility function for type casting a parsed json object from JSONParser.parse.
   * the top level object is either List or a Map depending on whether the
   * json was a javascript object or an array.  
   * perhaps there's a better SCALA way -- create implicit conversion 
   */
  def processJson(parsedJson:Any)(arrayFn:List[_]=>Unit)(objFn:Map[String,Any]=>Unit):Unit = {
    parsedJson match {
      case list:List[_] => arrayFn(list)        
      case map:Map[_,_] => objFn(map.asInstanceOf[Map[String,Any]])
      case unexpected =>  log.error("unexpected value in parsed Json: " + unexpected)        
    }
  }
  
  def jsonValueToString(value:Any) = {
    value match {
      case s:String => s
      case d:Double => d.toString
      case _ => throw new NotYetImplemented
    }
  }
  
  def jsonValueToInt(value:Any):Int = {
    value match {
      case d:Double => d.intValue
      case s:String => java.lang.Integer.parseInt(s)
      case _ => log.error("unexpected json value, hard to make into an Int: " + value); 0
    }
  }
  
  
  private def quote(str:String) = "\"" + str + "\""

  private def spaces(count:Int):String = {
    val sb = new StringBuilder
    (0 until count) foreach {_ => sb append " "}
    sb mkString
  }
  
  /** write a jsonMap out as a json string */
  def toJson(jsonMap:JsonMap, indent:Int, oneLine:Boolean):String = {
    val json = new StringBuilder()
    val space = spaces(indent)
    val startSpace = spaces(indent - 1)
    
    json append startSpace + "{"
    val iter = jsonMap.elements
    var first = true;
    while (iter.hasNext) {
      val (name, value) = iter.next
      if (!first)
        json append space
      else 
        first = false
        
      json append quote(name) + ":" + toJsonValue(value)
      if (iter.hasNext) {
        val separator = 
          if (oneLine)
            " "
          else 
            ",\n"  
        json append separator
      }
    }
    json append "}"
    json.mkString    
  }
  
  def toJsonOneLine(jsonMap:JsonMap):String = {
    toJson(jsonMap, 0, true)
  }
  
  def toJson(jsonMap:JsonMap):String = {
    toJson(jsonMap, 0, false)
  }

  
  /** convert a value object to a json value string.  */
  def toJsonValue(value:Any):String = {    
    value match {
      case ref:JsonRef => ref.toJson 
      case idRef:SyncableId => idRef.toJson 
      case array:Seq[_] => {
        val buf = new StringBuffer
        buf append "["
        val iter = array.elements
        while(iter.hasNext) {
          buf append toJsonValue(iter.next())			
          if (iter.hasNext)
        	buf append ",\n" 
        }
        buf append "]"
        buf.toString
      }
      case jsonMap:Map[_,_] => toJson(jsonMap.asInstanceOf[JsonMap])  // LATER - consider making jsonmap a class?
      case bool:Boolean => bool.toString
      case int:Integer => int.toString
      case double:Double => double.toString
      case string:String => quote(StringEscapeUtils.escapeJava(string))
      case null => "null"
      case none:net.liftweb.common.EmptyBox[_] => "null"	
      case other => quote(other.toString)  		// unfortunately, type erasure prevents matching on refs vs. vals here
    }
  }
  
}


/** translate primitive value objects parsed from json into reference objects (subclasses of AnyRef) */
object PrimitiveJsonValue {
  def unapply(any:Any):Option[AnyRef] = {
    any match {
      case Empty => Some(null)     // null is parsed as Empty
      case d:Double => Some(new java.lang.Double(d))
      case s:String => Some(s)
      case _ => None
    }    
  }

}