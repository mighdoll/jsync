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

import java.lang.ThreadLocal  

/** 
 * A dynamic variable that can be read only once.  Subsequent reads return None
 * 
 * SOON actually use a threadlocal for multithreading safely 
 */
class Takeable[T] {
  var value:Option[T] = None
  
  /** return the current value as an Option, and then clear the current value */
  def take():Option[T] = {
    val result = value
    value = None
    result
  }

  /** set the value of this Takeable */
  def set(newValue:Option[T]) = 
    value = newValue
  
  /** set the current value during the execution of the variable */
  def withValue[S](newValue:T)(body: =>S): S = {
    val oldValue = value
    value = Some(newValue)
    try {body} finally {
      value = oldValue
    }
  }
  
}
