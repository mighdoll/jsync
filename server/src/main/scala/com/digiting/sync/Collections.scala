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
import com.digiting.sync.aspects.Observable
import Observation._
 
class SyncableMap extends Syncable {
  def kind = "$sync.map"
}

class SyncableSet[T] extends Syncable with mutable.Set[T] {
  def kind = "$sync.set"  
  val set = new mutable.HashSet[T]  
  
  def -=(elem:T) = {
    Observation.notify(new RemoveChange(this, elem));
    set -= elem
  }
  
  def +=(elem:T) = {
    Observation.notify(new PutChange(this, elem));
    set += elem
  }
  
  def contains(elem:T) = set contains elem
  def size = set size
  def elements = set elements
}

class SyncableSortedSet extends Syncable {
  def kind = "$sync.sortedSet"
}

