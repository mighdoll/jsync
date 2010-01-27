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
import net.lag.logging.Logger
import com.digiting.sync.aspects.Observable


/** utility function for identifying property methods in scala classes */
object Properties {
  private val propertyMethod = """([^$]+)_[$]eq""".r
  
  def propertySetterName(methodName:String):Option[String] = {
    propertyMethod findFirstMatchIn methodName match {
      case Some(m) => Some(m.group(1))
      case _ => None
    }
  }
}

/**
 * Reflection based access to a single property in a class
 */
class PropertyAccessor(val name:String, setter:Method, getter:Method) {
  val log = Logger("PropertyAccessor")
  log.trace("creating accessor: %s %s ", name, setter);
  
  assert (name == Properties.propertySetterName(setter.getName).getOrElse(""))
  assert (setter.getParameterTypes.length == 1)
  val propertyClass:Class[_] = setter.getParameterTypes()(0)  // first parameter

  def set(target: AnyRef, value:AnyRef) = {
    log.trace("set()  (%s).%s[%s] = %s", target, name, propertyClass.getSimpleName, value)
    setter.invoke(target, value)
  }
  
  def get(target: AnyRef):Any = {
    getter.invoke(target)
  }
}

/**
 * Reflection based access to a properties in a scala class.  Properties are
 * currently defined as any scala getter/setter pair (value(), value_=()).
 * LATER limit properties to only certain getter/setters.
 */
class ClassAccessor(clazz:Class[_], ignoreMethods:String=>Boolean) {
  val log = Logger("ClassAccessor")
  val propertyAccessors:mutable.Map[String, PropertyAccessor] = mutable.Map()
  val referenceProperties:mutable.Set[PropertyAccessor] = mutable.Set()
  
  val theClass = clazz
  def set(target:AnyRef, property:String, value:AnyRef) = {
    propertyAccessors get property match {
      case Some(accessor) => accessor.set(target, value)
      case None => 
        log.error("set: property: %s not found on class: ", property, clazz)         
    }
  }
  
  def references(target:AnyRef):Iterable[AnyRef] = { 
    for (prop <- referenceProperties) 
      yield prop.get(target).asInstanceOf[AnyRef]    
  }

  // handy map of methods by name
  val methods:mutable.Map[String, Method] = mutable.Map()
  for (method <- clazz.getMethods) 
    methods + (method.getName -> method)

  // SCALA style how do organize this initialization code?

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
        case _ => log.warning("ignoring property: " + propertyName + " on class: " + 
                             clazz.getName + ".  It has a setter, but no getter")
      }
    }
  }
}

/**
 * Convenience routines for working with syncable classes and instances via reflection.  
 * Maintains a cache of accessors for each type.
 */
object SyncableAccessor {
  type AnyRefClass = AnyRef
  val accessors:mutable.Map[Class[_], ClassAccessor] = mutable.Map()
  accessors += (classOf[Receiver] -> (null:ClassAccessor))
  
  /* get an accessor for the given class */
  def get(clazz:Class[_]):ClassAccessor = {
    synchronized {
      accessors get clazz getOrElse {      
        val accessor:ClassAccessor = new ClassAccessor(clazz, SyncableInfo.isReserved)      
        accessors += ((clazz, accessor))  // SCALA why doesn't clazz -> accessor work? , compiler bug?
        accessor
      } 
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


/**
 * Cache of ClassAccessors and other convenience routines for working with scala 
 * classes and instances via reflection
 */
object Accessor {
  /* currently, this uses the SyncableAccessor.  This means that object
     references to reserved Syncable fields are ignored.  Fine for me for now,
     but LATER give this a separate cache if it's used for another purpose.  */
  def references(obj:AnyRef):Iterable[AnyRef] = SyncableAccessor.references(obj)
  
  /* Collect every reference to an Observable object directly from a given object
   * 
   * @param base  object instance to scan for references
   */
  def observableReferences(obj:AnyRef):Seq[Syncable] = {
    val refs = new mutable.ListBuffer[Syncable]()
    references(obj) foreach {_ match {
      case ref:Syncable => refs + ref
      case _ =>      
    } }
    obj match {
      case collection:SyncableCollection => 
        refs ++ collection.syncableElements
//        for (elem <- collection.syncableElements) {
//          Console println ("observable element: " + elem)
//        }
      case _ =>
    }

    refs.toSeq
  }      


}

/* LATER move the Syncable, isReserved stuff out of this file -- it should be generic.  */
