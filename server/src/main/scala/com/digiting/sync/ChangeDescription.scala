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
import java.io.Serializable

/** 
 * Subclases describe details of a change to an observable object or collection.
 */
abstract sealed class ChangeDescription {
  val source = Observers.currentMutator.value;
  val target:SyncableId
  
  override def toString = {this.getClass.getSimpleName + " target: " + target + " mutator: " + source}
  def references:List[SyncableId] = List(target)
}

case class VersionChange(val old:String, val now:String) {
  override def toString = "old=" + old + " now=" + now
}

abstract sealed class StorableChange extends ChangeDescription

/** change to a mutable object or collection */
abstract sealed class DataChange(val versionChange:VersionChange) extends StorableChange {
   override def toString = super.toString + " " + versionChange
}

/** change to a collection */
abstract sealed class CollectionChange(versionChange:VersionChange) extends DataChange(versionChange)

/** changes to a collection's membership. LATER get rid of this */
abstract sealed class MembershipChange(val operation:String, 
  val newValue:SyncableId, val oldValue:SyncableId, versionChange:VersionChange) 
  extends CollectionChange(versionChange) {
  override def toString = (super.toString + " ." + operation + "(" + newValue + ")  was(" + oldValue + ")")
  override def references = super.references ++ List(newValue)
}

 
//       -----------------  simple Data changes --------------------  
 
/** change to a property */
case class PropertyChange(val target:SyncableId, property:String, val newValue:SyncableValue, 
                          val oldValue:SyncableValue, versions:VersionChange) 
      extends DataChange(versions) {
  override def toString = (super.toString + " ." + property + " = " + newValue + " was:" + oldValue)
  override def references = super.references ++ newValue.reference.toList
}  
      
/** create a new object.  */  
case class CreatedChange(val target:SyncableReference,  
    pickled:Pickled, versions:VersionChange) extends DataChange(versions)

/** delete an object. */
case class DeletedChange(val target:SyncableReference, 
    versions:VersionChange) extends DataChange(versions)

 
//       -----------------  collection Data changes --------------------  

trait SeqChange

/** remove all contents from a collection */
case class ClearChange(val target:SyncableId, val members:Iterable[SyncableId],
  versions:VersionChange) extends CollectionChange(versions) with SeqChange {
  def operation = "clear"
}

/** add to a set*/
case class PutChange(val target:SyncableId, newVal:SyncableReference, versions:VersionChange) 
  extends MembershipChange("put", newVal, null, versions)  {
  override def references = newVal.id :: super.references
}
  
/** remove from a set*/  
case class RemoveChange(val target:SyncableId, oldVal:SyncableReference, versions:VersionChange) 
  extends MembershipChange("remove", null, oldVal, versions) 

/* remove from a seq*/
case class RemoveAtChange(val target:SyncableId, at:Int, oldVal:SyncableId,
    versions:VersionChange) 
  extends MembershipChange("removeAt", null, oldVal, versions) with SeqChange {
  override def toString = (super.toString + " at:" + at)
}
  
/** add to a seq */
case class InsertAtChange(val target:SyncableId, newVal:SyncableReference, at:Int,
    versions:VersionChange) 
  extends MembershipChange("insertAt", newVal, null, versions) with SeqChange {
  
  override def toString = (super.toString + " at:" + at)
  override def references = newVal.id :: super.references 
}  
/** rearrange a seq */
case class MoveChange(val target:SyncableId, fromDex:Int, toDex:Int, 
    versions:VersionChange) extends CollectionChange(versions) {
  def operation = "move"
}
  
/** remove from a map */
case class RemoveMapChange(val target:SyncableId, key:Serializable, oldValue:SyncableReference, 
    versions:VersionChange) extends CollectionChange(versions)
/** add/update to a map */
case class PutMapChange(val target:SyncableId, key:Serializable, oldValue:Option[SyncableReference], 
    newValue:SyncableReference, versions:VersionChange) extends CollectionChange(versions) {
      
  override def references = newValue.id :: super.references 
}

//       -----------------  observe changes --------------------  

sealed abstract class ObservingChange extends StorableChange
case class ObserveChange(val target:SyncableId, val watcher:PickledWatch) extends ObservingChange
case class EndObserveChange(val target:SyncableId, val watcher:PickledWatch) extends ObservingChange

//       -----------------  watch set changes --------------------  

sealed abstract class WatchChange(val watcher:DeepWatch) extends ChangeDescription 

/** added to the DeepWatch  */
case class BeginWatch(val target:SyncableId, val newValue:SyncableId, 
    val watch:DeepWatch) extends WatchChange(watch) {
  override def toString = {this.getClass.getSimpleName + " root: " + target + "mutator: " + source + " newWatch: " + newValue + "  watcher: " + watcher}  
  override def references = newValue :: super.references
}

/** dropped from the DeepWatch  */
case class EndWatch(val target:SyncableId, val oldValue:SyncableId,
    val watch:DeepWatch) extends WatchChange(watch) 

/** initial state of a collection */      
case class BaseMembership(val target:SyncableId, members:Seq[SyncableId], 
    val watch:DeepWatch) extends WatchChange(watch) {
  override def references = super.references ++ members   
}

