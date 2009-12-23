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
import JsonObject._

/**
 * NOT CURRENTLY USED
 * 
 * A group of CRUD operations on syncable objects.  Used by
 * the Partition to bundle up an atomic transaction of changes.
 */
class PartitionTransaction {
  // log of changes in this transaction
  val puts = new mutable.Queue[JsonMap]
  val edits = new mutable.Queue[ChangeDescription]

  /** add a create operation to the log */
  def add(frozenObj:JsonMap) = puts += frozenObj
  
  /** add an update to the log */
  def edit(change:ChangeDescription) = edits += change
  
  /** apply all changes to the specified pool of instances */
  def apply(pool:InstancePool) = {
    throw new NotYetImplemented    
  }
  
  /** erase this.  CONSIDER is this needed? */
  def clear() = {
    edits.clear
    puts.clear
  }  
}
