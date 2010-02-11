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
import collection.mutable.{Buffer, ListBuffer, HashMap}


class MultiBuffer[K, V, S <: Seq[V]] extends HashMap[K, S] {
  def insert(key:K, value:V, at:Int) {
    buffer(key) insert(at, value)
  }
  
  def append(key:K, value:V) {
    buffer(key) append(value)
  }
  
  def remove(key:K, at:Int):V = {
    buffer(key) remove(at)
  }
  
  private[this] def buffer(key:K):Buffer[V] = {
    get(key) match {
      case Some(found) => 
        found.asInstanceOf[Buffer[V]]
      case None =>
        val newBuf = new ListBuffer[V]
        this(key) = newBuf.asInstanceOf[S]
        newBuf
    }    
  }  
}

trait MapMap[K1, K,V] extends HashMap[K1, HashMap[K,V]] {
  def update(mapKey:K1, tuple:Tuple2[K,V]) = {
    getMap(mapKey) += tuple
  }
  
  def remove(mapKey:K1, key:K) = {
    getMap(mapKey) -= key
  }
  
  private[this] def getMap(mapKey:K1):HashMap[K,V] = {
    get(mapKey) getOrElse {
      val newMap = new HashMap[K,V]
      this(mapKey) = newMap
      newMap
    }
  }
}