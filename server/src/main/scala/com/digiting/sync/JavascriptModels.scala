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
import collection._
import net.lag.logging.Logger

/** generate a javascript file with definitions for the model formats */
object JavascriptModels {
  val log = Logger("JavascriptModels")
  val extractNameSpace = """(.*)\..*""".r
  val emitted = new mutable.HashSet[String]
  val nameSpaces = new mutable.HashSet[String]
  
  def generate():String = {       
    nameSpaces.clear
    emitted.clear
    
    def saveNameSpace(kind:String) {      
      kind match {
        case extractNameSpace(name) =>
          nameSpaces += name
        case _ =>
          log.error("namespace not found in kind: %s ", kind)
      }
    }
      
    val lines = 
      for {
        (kind, accessor) <- SyncManager.metaAccessors
        if !(classOf[LocalOnly].isAssignableFrom(accessor.clazz))
        ignored = saveNameSpace(kind)}
        yield String.format("""%s = $sync.manager.defineKind("%s", %s);""", 
                            kind, kind, propertiesArray(accessor))
    nameSpaceJs(nameSpaces) + "\n\n" + lines.mkString("\n")
  }
  
  def nameSpaceJs(names:Set[String]):String = {
    val lines = 
      for (name <- names) 
        yield oneNameSpaceJs(name)
    lines.filter(_.length > 1).mkString("\n")
  }
     
  def oneNameSpaceJs(name:String):String = {
    var prefix = "";
    val lines =  
      for (domain <- name.split('.').toList) 
        yield {
          if (prefix == "") {
            prefix = domain;
            emitting(prefix, "var %s = %s || {}; ", prefix, prefix)
          } else {
            prefix = prefix + "." + domain
            emitting(prefix, "%s = %s || {};", prefix, prefix)
          }
        }
    
    lines.filter(_.length > 1).mkString("\n")
  }
  
  def emitting(prefix:String, msg:String, params:String*):String = {
    if (!emitted.contains(prefix)) {
      val toEmit = String.format(msg, params:_*)
      emitted += prefix
      toEmit
    } else {
      ""
    }
  }
  
  
  def propertiesArray(accessor:ClassAccessor):String = {
    val props = 
      for ((prop, _) <- accessor.propertyAccessors) 
        yield String.format("""'%s'""", prop)
    
    props.mkString("[", ", ", "]")    
  }
  
}
