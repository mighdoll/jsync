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
import net.lag.logging.Logger
import com.digiting.util.Matching.partialMatch

/** Holds a value that's validated as legal for synchronization.
 * 
 * Currently, primitive numeric types, strings,
 * and references to other syncable objects may be synchronized.
 */
class SyncableValue {
  object Uninitialized
  private[this] var validated:Any = Uninitialized  // should be overriden by constructor
  
  def value:Any = validated
  
  def this(value:Null) = { this(); validated = value}
  def this(value:SyncableReference) = { this(); validated = value}
  def this(value:String) = { this(); validated = value}
  def this(value:Boolean) = { this(); validated = value}
  def this(value:Byte) = { this(); validated = value}
  def this(value:Char) = { this(); validated = value}
  def this(value:Short) = { this(); validated = value}
  def this(value:Int) = { this(); validated = value}
  def this(value:Long) = { this(); validated = value}
  def this(value:Float) = { this(); validated = value}
  def this(value:Double) = { this(); validated = value}
    
  override def toString = {
    validated match {
      case null => "null"
      case x => x.toString
    }
  }

  def reference:Option[SyncableId] = partialMatch(validated) {
    case ref:SyncableReference => ref.id
  }
}

object SyncableValue {
  val log = Logger("SyncableValue")
  def apply(value:SyncableReference) = new SyncableValue(value)
  def apply(value:String) = new SyncableValue(value)
  def apply(value:Boolean) = new SyncableValue(value)
  def apply(value:Byte) = new SyncableValue(value)
  def apply(value:Char) = new SyncableValue(value)
  def apply(value:Short) = new SyncableValue(value)
  def apply(value:Int) = new SyncableValue(value)
  def apply(value:Long) = new SyncableValue(value)
  def apply(value:Float) = new SyncableValue(value)
  def apply(value:Double) = new SyncableValue(value)
  def apply(value:Null) = new SyncableValue(value)
  
  def convert(value:Any):SyncableValue = {
    value match {
      case s:Syncable => new SyncableValue(SyncableReference(s))
      case v:String => new SyncableValue(v)
      case v:Boolean => new SyncableValue(v)
      case v:Byte => new SyncableValue(v)
      case v:Char => new SyncableValue(v)
      case v:Short => new SyncableValue(v)
      case v:Int => new SyncableValue(v)
      case v:Long => new SyncableValue(v)
      case v:Float => new SyncableValue(v)
      case v:Double => new SyncableValue(v)
      case null => new SyncableValue(null)
      case r:AnyRef => 
        log.error("unexpected argument: %s of type: %s", r, r.getClass)
        throw new IllegalArgumentException        
      case x => 
        log.error("unexpected argument: %s ", x)
        throw new IllegalArgumentException
    }
  }
}


