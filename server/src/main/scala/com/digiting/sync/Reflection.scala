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
import java.lang.reflect.Method
import _root_.net.liftweb.util._

object Properties {
  val propertyMethod = "(.*)_[$]eq".r
  
  def propertySetterName(methodName:String):Option[String] = {
    propertyMethod findFirstMatchIn methodName match {
      case Some(m) => Some(m.group(1))
      case _ => None
    }
  }
}

class PropertyAccessor(val name:String, setter:Method, getter:Method) {
//  Console println "creating accessor: " + name + "  " + setter
  
  assert (name == Properties.propertySetterName(setter.getName).getOrElse(""))
  assert (setter.getParameterTypes.length == 1)
  val propertyClass:Class[_] = setter.getParameterTypes()(0)  // first parameter

  def set(target: AnyRef, value:AnyRef) = {
    assert (value == null || propertyClass.isAssignableFrom(value.getClass))
//    Console println "  (" + target + ")." + name + " = " + value      

    setter.invoke(target, value)
  }
  
  def get(target: AnyRef):Any = {
    getter.invoke(target)
  }
}

class ClassAccessor(clazz:Class[_], ignoreMethods:String=>Boolean) {
  val propertyAccessors:mutable.Map[String, PropertyAccessor] = mutable.Map()
  val referenceProperties:mutable.Set[PropertyAccessor] = mutable.Set()
  
  val theClass = clazz
  def set(target:AnyRef, property:String, value:AnyRef) = {    
    propertyAccessors(property).set(target, value)
  }
  
  def references(target:AnyRef):Iterable[AnyRef] = { 
    for (prop <- referenceProperties) 
      yield prop.get(target).asInstanceOf[AnyRef]    
  }

  // handy map of methods by name
  val methods:mutable.Map[String, Method] = mutable.Map()
  for (method <- clazz.getMethods) 
    methods + (method.getName -> method)
  
  // walk through methods looking for setters
  for (method <- clazz.getMethods) { 
      val propertyNameOpt = Properties.propertySetterName(method.getName)
      for (propertyName <- propertyNameOpt if !ignoreMethods(propertyName)) {
          // find the matching getter too
          val getterOpt = methods get propertyName
          getterOpt match {
            case Some(getter) => {
              // create and save property accessor
	          val accessor = new PropertyAccessor(propertyName, method, getter)
           
		      propertyAccessors + (propertyName -> accessor)
	          if (classOf[AnyRef].isAssignableFrom(accessor.propertyClass))
	            referenceProperties + accessor
	              
	        }
            case _ => Log.warn("ignoring property: " + propertyName + " on class: " + 
                                 clazz.getName + ".  It has a setter, but no getter")
          }
      }
  }
}

object SyncableAccessor {
  type AnyRefClass = AnyRef
  val accessors:mutable.Map[Class[_], ClassAccessor] = mutable.Map()

  /* get an accessor for the given class */
  def get(clazz:Class[_]):ClassAccessor = {
    accessors get clazz getOrElse {      
      val accessor = new ClassAccessor(clazz, SyncableInfo.isReserved)      
      accessors + ((clazz, accessor))  // WHY doesn't clazz -> accessor work?
      accessor
    } 
  }
  
  /* retrieve all references from an instance */
  def references(obj:AnyRef):Iterable[AnyRef] = {
    val accessor = get(obj.getClass)
    accessor.references(obj)
  }
  
  def properties(obj:AnyRef):Iterator[(String,Any)] = {
    val toClass = get(obj.getClass)
    for (toProperty <- toClass.propertyAccessors.values) 
      yield (toProperty.name, toProperty.get(obj))
  }
}

object Accessor {
  /* currently, this uses the SyncableAccessor.  This means that objects
     references to reserved Syncable fields are ignored.  Fine for me for now,
     but LATER give this a separate cache if it's used for another purpose.  */
  def references(obj:AnyRef):Iterable[AnyRef] = SyncableAccessor.references(obj)
}


/* MOVE the Syncable, isReserved stuff out of this file -- it should be generic.  */
