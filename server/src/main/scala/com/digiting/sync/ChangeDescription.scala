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

abstract class DataChange(val versionChange:VersionChange) extends ChangeDescription {  
}

case class VersionChange(val old:String, val current:String)

abstract class WatchChange extends ChangeDescription

/** changes to a collection's membership. */
abstract class MembershipChange(val operation:String, 
  val newValue:SyncableId, val oldValue:SyncableId, versionChange:VersionChange) 
  extends DataChange(versionChange) {
  override def toString = (super.toString + " ." + operation + "(" + newValue + ")  was(" + oldValue + ")")
}

 
//       -----------------  simple Data changes --------------------  
 
/** change to a property */
case class PropertyChange(val target:SyncableId, property:String, val newValue:SyncableValue, 
                          val oldValue:SyncableValue, versions:VersionChange) 
      extends DataChange(versions) {
  override def toString = (super.toString + " ." + property + " = " + newValue + " was:" + oldValue)
}  
      
/** create a new object.  */  // CONSIDER SCALA type parameters are a hassle for pickling/unpickling.  manifest?  
case class CreatedChange[T <: Syncable](val target:SyncableReference,  
    pickled:Pickled[T], versions:VersionChange) extends DataChange(versions)

/** delete an object. */
case class DeletedChange(val target:SyncableReference, 
    versions:VersionChange) extends DataChange(versions)

 
//       -----------------  collection Data changes --------------------  

/** remove all contents from a collection */
case class ClearChange(val target:SyncableId, val members:Iterable[SyncableId],
  versions:VersionChange) extends DataChange(versions) {
  def operation = "clear"
}

/** add to a set*/
case class PutChange(val target:SyncableId, newVal:SyncableReference, versions:VersionChange) 
  extends MembershipChange("put", newVal, null, versions)  
/** remove from a set*/  
case class RemoveChange(val target:SyncableId, oldVal:SyncableReference, versions:VersionChange) 
  extends MembershipChange("remove", null, oldVal, versions) 

/* remove from a seq*/
case class RemoveAtChange(val target:SyncableId, at:Int, oldVal:SyncableId,
    versions:VersionChange) 
  extends MembershipChange("removeAt", null, oldVal, versions) {
  override def toString = (super.toString + " at:" + at)
}
/** add to a seq */
case class InsertAtChange(val target:SyncableId, newVal:SyncableReference, at:Int,
    versions:VersionChange) 
  extends MembershipChange("insertAt", newVal, null, versions) {
  
  override def toString = (super.toString + " at:" + at)
}  
/** rearrange a seq */
case class MoveChange(val target:SyncableId, fromDex:Int, toDex:Int, 
    versions:VersionChange) extends DataChange(versions) {
  def operation = "move"
}
  
/** remove from a map */
case class RemoveMapChange(val target:SyncableId, oldKey:Any, oldValue:Any, 
    versions:VersionChange) extends DataChange(versions)
/** add/update to a map */
case class PutMapChange(val target:SyncableId, newKey:Any, newValue:Any,
    versions:VersionChange) extends DataChange(versions)

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

