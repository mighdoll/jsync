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
package com.digiting.util

object TryCast {
  /** try doing a cast at runtime.  Note this is slower than compile time match tests.  */
  def tryCast[T](instance:Any, clazz:Class[T]):Option[T] = {
    instance match {
      case ref:AnyRef if clazz.isAssignableFrom(ref.getClass) => 
        Some(ref.asInstanceOf[T])
      case _ => None
    }
  }
  
  /** cast Any as Option[String] */
  def matchOptString(any: Any):Option[String] = {
    any match {
      case Some(str:String) => Some(str)
      case _ => None
    }
  }
    
  /** cast String as Option[String] */
  def matchString(any: Any):Option[String] = {
    any match {
      case str:String => Some(str)
      case _ => None
    }
  }

  /** possibly null string to Option[String] */
  def nullableStringToOption(value:String):Option[String] = {
    if (value != null) {
      Some(value)
    } else {
      None
    }    
  }

}
