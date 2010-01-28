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


/** 
 * Subclases describe details of a change to an observable object or collection.
 */
abstract class ChangeDescription {
  val source = Observers.currentMutator.value;
  val target:SyncableId
  
  override def toString = {this.getClass.getSimpleName + " target: " + target + " mutator: " + source}
}

/** change to a property */
case class PropertyChange(val target:SyncableId, property:String, val newValue:Any, val oldValue:Any) 
      extends ChangeDescription{
  override def toString = (super.toString + " ." + property + " = " + newValue + " was:" + oldValue)
}
  
/** create a new object.  TODO change this to include a serialized syncable. */
case class CreatedChange(val target:SyncableId) extends ChangeDescription

/** changes to a collection's membership. */
abstract class MembershipChange(val operation:String, 
  val newValue:SyncableId, val oldValue:SyncableId) extends ChangeDescription {
  override def toString = (super.toString + " ." + operation + "(" + newValue + ")  was(" + oldValue + ")")
}

/** remove all contents from a collection */
case class ClearChange(val target:SyncableId, val members:Iterable[SyncableId]) extends ChangeDescription {
  def operation = "clear"
}

  /* set changes*/
case class PutChange(val target:SyncableId, newVal:SyncableId) 
  extends MembershipChange("put", newVal, null)  
  
case class RemoveChange(val target:SyncableId, oldVal:SyncableId) 
  extends MembershipChange("remove", null, oldVal) 

  /* seq changes */
case class RemoveAtChange(val target:SyncableId, at:Int, oldVal:SyncableId) 
  extends MembershipChange("removeAt", null, oldVal) {
  override def toString = (super.toString + " at:" + at)
}
case class InsertAtChange(val target:SyncableId, newVal:SyncableId, at:Int) 
  extends MembershipChange("insertAt", newVal, null) {
  
  override def toString = (super.toString + " at:" + at)
}  
case class MoveChange(val target:SyncableId, fromDex:Int, toDex:Int)
  extends ChangeDescription {
  def operation = "move"
}
  
  /* map changes */
case class RemoveMapChange(val target:SyncableId, oldKey:Any, oldValue:Any) 
  extends ChangeDescription

case class UpdateMapChange(val target:SyncableId, newKey:Any, newValue:Any) 
  extends ChangeDescription

  
/* changes to the watch set from DeepWatch.  */
case class WatchChange(val target:SyncableId, val newValue:SyncableId, val watcher:AnyRef) extends ChangeDescription {
  override def toString = {super.toString + " newWatch: " + newValue + "  watcher: " + watcher}  
}
case class UnwatchChange(val target:SyncableId, val oldValue:SyncableId) extends ChangeDescription

/** initial state of a collection */      
case class BaseMembership(val target:SyncableId, members:Seq[SyncableId]) 
  extends ChangeDescription

