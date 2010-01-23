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
import com.digiting.sync.aspects.Observable


/** 
 * Subclases describe details of a change to an observable object or collection.
 */
abstract class ChangeDescription(val target:Observable) {
  val source = Observers.currentMutator.value;
  
  override def toString = {this.getClass.getSimpleName + " target: " + target + " mutator: " + source}
}

/** change to a property */
case class PropertyChange(changed:Observable, property:String, val newValue:Any, val oldValue:Any) 
      extends ChangeDescription(changed){
  override def toString = (super.toString + " ." + property + " = " + newValue + " was:" + oldValue)
}
  
/** create a new object */
case class CreatedChange(created:Observable) extends ChangeDescription(created)

/** changes to a collection's membership. */
abstract class MembershipChange(target:Observable, val operation:String, 
  val newValue:Any, val oldValue:Any) extends ChangeDescription(target) {
  override def toString = (super.toString + " ." + operation + "(" + newValue + ")  was(" + oldValue + ")")
}

/** remove all contents from a collection */
case class ClearChange(collection:Observable, members:List[Observable])
  extends ChangeDescription(collection) {
  def operation = "clear"
}

  /* set changes*/
case class PutChange(changed:Observable, newVal:Any) 
  extends MembershipChange(changed, "put", newVal, null)  
  
case class RemoveChange(changed:Observable, oldVal:Any) 
  extends MembershipChange(changed, "remove", null, oldVal) 

  /* seq changes */
case class RemoveAtChange(changed:Observable, at:Int, oldVal:Any) 
  extends MembershipChange(changed, "removeAt", null, oldVal) {
  override def toString = (super.toString + " at:" + at)
}
case class InsertAtChange(changed:Observable, newVal:Any, at:Int) 
  extends MembershipChange(changed, "insertAt", newVal, null) {
  
  override def toString = (super.toString + " at:" + at)
}
case class MoveChange(collection:Observable, fromDex:Int, toDex:Int)
  extends ChangeDescription(collection) {
  def operation = "move"
}
  
  /* map changes */
case class RemoveMapChange(changed:Observable, oldKey:Any, oldValue:Any) 
  extends ChangeDescription(changed)

case class UpdateMapChange(changed:Observable, newKey:Any, newValue:Any) 
  extends ChangeDescription(changed)

  
/* changes to the watch set from DeepWatch.  */
case class WatchChange(val root:Observable, val newValue:Observable, val watcher:AnyRef) extends ChangeDescription(root) {
  override def toString = {super.toString + " newWatch: " + newValue + "  watcher: " + watcher}  
}
case class UnwatchChange(val root:Observable, val oldValue:Observable) extends ChangeDescription(root)

/** initial state of a collection */      
case class BaseMembership(collection:Observable, members:List[Observable]) 
  extends ChangeDescription(collection)

