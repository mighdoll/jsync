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

abstract class DataChange extends ChangeDescription {  
//  val oldVersion:String
//  val newVersion:String
}

abstract class WatchChange extends ChangeDescription

/** changes to a collection's membership. */
abstract class MembershipChange(val operation:String, 
  val newValue:SyncableId, val oldValue:SyncableId) extends DataChange {
  override def toString = (super.toString + " ." + operation + "(" + newValue + ")  was(" + oldValue + ")")
}

 
//       -----------------  simple Data changes --------------------  
 
/** change to a property */
case class PropertyChange(val target:SyncableId, property:String, val newValue:SyncableValue, 
                          val oldValue:SyncableValue) 
      extends DataChange {
  override def toString = (super.toString + " ." + property + " = " + newValue + " was:" + oldValue)
}  
/** create a new object.  TODO change this to include a serialized syncable. */
case class CreatedChange(val target:SyncableId) extends DataChange

 
//       -----------------  collection Data changes --------------------  

/** remove all contents from a collection */
case class ClearChange(val target:SyncableId, val members:Iterable[SyncableId]) extends DataChange {
  def operation = "clear"
}

/** add to a set*/
case class PutChange(val target:SyncableId, newVal:SyncableId) 
  extends MembershipChange("put", newVal, null)  
/** remove from a set*/  
case class RemoveChange(val target:SyncableId, oldVal:SyncableId) 
  extends MembershipChange("remove", null, oldVal) 

/* remove from a seq*/
case class RemoveAtChange(val target:SyncableId, at:Int, oldVal:SyncableId) 
  extends MembershipChange("removeAt", null, oldVal) {
  override def toString = (super.toString + " at:" + at)
}
/** add to a seq */
case class InsertAtChange(val target:SyncableId, newVal:SyncableId, at:Int) 
  extends MembershipChange("insertAt", newVal, null) {
  
  override def toString = (super.toString + " at:" + at)
}  
/** rearrange a seq */
case class MoveChange(val target:SyncableId, fromDex:Int, toDex:Int)
  extends DataChange {
  def operation = "move"
}
  
/** remove from a map */
case class RemoveMapChange(val target:SyncableId, oldKey:Any, oldValue:Any) 
  extends DataChange
/** add/update to a map */
case class UpdateMapChange(val target:SyncableId, newKey:Any, newValue:Any) 
  extends DataChange

//       -----------------  watch set changes --------------------  

/* added to the DeepWatch  */
case class BeginWatch(val target:SyncableId, val newValue:SyncableId, 
                      val watcher:AnyRef) extends WatchChange {
  override def toString = {super.toString + " newWatch: " + newValue + "  watcher: " + watcher}  
}
/* dropped from the DeepWatch  */
case class EndWatch(val target:SyncableId, val oldValue:SyncableId) extends WatchChange

/** initial state of a collection */      
case class BaseMembership(val target:SyncableId, members:Seq[SyncableId]) 
  extends WatchChange

