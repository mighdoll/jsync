package com.digiting.sync
import com.digiting.sync.aspects.Observable


/** 
 * Subclases describe details of a change to an observable object or collection.
 */
abstract class ChangeDescription(val target:Observable) {
  val source = Observers.currentMutator.value;
}

/** change to a property */
case class PropertyChange(changed:Observable, property:String, val newValue:Any, val oldValue:Any) 
      extends ChangeDescription(changed){
  override def toString = (target + "." + property + " = " + newValue + " was:" + oldValue + " source: " + source)
}

/** changes to a collection. */
abstract class MembershipChange(target:Observable, val operation:String, 
  val newValue:Any, val oldValue:Any) extends ChangeDescription(target) {
  def operationTarget:Any
  override def toString = (target + "+=" + newValue + " -=" + oldValue + " source: " + source)
}
case class PutChange(changed:Observable, newVal:Any) extends MembershipChange(changed, "put", newVal, null) {
  override def operationTarget = newVal
}  
case class RemoveChange(changed:Observable, oldVal:Any) extends MembershipChange(changed, "remove", null, oldVal) {
  override def operationTarget = oldVal
}
case class RemoveMapChange(changed:Observable, oldKey:Any, oldValue:Any) extends ChangeDescription(changed)
case class UpdateMapChange(changed:Observable, newKey:Any, newValue:Any) extends ChangeDescription(changed)

/** changes to the set of subscribed objects.  */
case class WatchChange(val root:Observable, val newValue:Observable) extends ChangeDescription(root)
case class UnwatchChange(val root:Observable, val oldValue:Observable) extends ChangeDescription(root)
  
/** create a new object */
case class CreatedChange(created:Observable) extends ChangeDescription(created)