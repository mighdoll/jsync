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

import collection._

/**
 * LATER, reconsider using something from the scala libraries for this
 */
class MultiMap[A,B] {
  val map = new mutable.HashMap[A,mutable.Set[B]]

  def +=(key:A, value:B) = this + (key,value) 
  
  def +(key:A, value:B) {
    val set:mutable.Set[B] = 
      map get key match {
      case Some(set) => set 
      case None => {
        val set = mutable.Set[B]()
        map + (key -> set)
        set
      }
    }
    set + value
  }

  def -=(key:A, value:B) = this - (key, value)
  
  def -(key:A, value:B) {
    map get key foreach { set =>
      set - value
      if (set.isEmpty) {
        map - key
      }
    }
  }
  
  def keys = map.keys
  
  def removeValues(key:A) (fn:(B)=>Boolean) {
    map get key foreach { set =>
      for (value <- set) {
        if (fn(value))
          set - value
      }
      if (set.isEmpty)
        map - key
    }
  }
  
  def foreach(fn:(A,B)=>Unit) {
    for ((key,set) <- map) {
      for (value <- set) {
        fn(key, value)
      }
    }
  }
  
  def foreachValue(key:A)(fn:(B)=>Unit) {
    for (set <- (map get key);
         value <- set) {
      fn(value)      
    }
  }
  
  private lazy val emptySet = new immutable.EmptySet[B]()

  def get(key:A):Set[B] = {
    map get key match {
      case Some(set) => set
      case None => emptySet
    }
  }
  
  def size = {
    var count = 0
    foreach {(_,_) => 
      count += 1 
    }
    count
  }
  
  def clear() = map.clear
  
  def isEmpty = map.isEmpty
    
}
