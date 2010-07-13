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
import scala.util.DynamicVariable
import Log2._

trait DebugId {
  private var nextId = 0
  def debugId() = {
    val next = nextId
    nextId = nextId + 1
    next
  }
}
object DynamicOnce extends DebugId

/** 
 * A dynamic variable that can be read only once.  Subsequent reads return None
 */
class DynamicOnce[T] {
  var current = new DynamicVariable[Option[T]](None)
  val debugId = DynamicOnce.debugId()
  implicit private val log = logger("DynamicOnce")
  
  /** return the current value as an Option, and then clear the current value */
  def take():Option[T] = {
    trace2("#%s take(): %s", debugId, current.value)
    val result = current.value
    current.value = None
    result
  }

  /** set the value of this Takeable */
  def set(newValue:Option[T]) = {
    trace2("#%s set(): %s", debugId, newValue)
    current.value = newValue
  }
  
  /** set the current value during the execution of the variable */
  def withValue[S](newValue:T)(body: => S): S = {
    trace2("#%s withValue(): %s", debugId, newValue)
    val oldValue = current.value
    current.value = Some(newValue)
    try {
      body
    } finally {
      current.value = oldValue
    }
  }
  
  def hasValue = current.value.isEmpty
  
}
