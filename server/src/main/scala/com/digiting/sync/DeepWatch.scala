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
import net.liftweb.util.Log

import com.digiting.sync.aspects.Observable
import Observers._
import Accessor.observableReferences

object DeepWatchDebug {
  var nextId = -1
  def nextDebugId() = {nextId += 1; nextId}
}
/** 
 * Watch all observable objects referenced by this object or any of its connected set of references.  
 * Changes to objects in the connected set are reported to watchers, as are objects added or 
 * removed from the connected set (e.g. an object is removed from the connected set when the
 * last reference to it from the connected set is removed).  
 * 
 * LATER Refernce cycles in the connected set are detected intermittently (currently not at all).
 *
 * Internally, DeepWatch maintains a reference count for every object in the connected set of objects
 * referenced from the root, and the Observers facility to watch for changes to objects in the
 * connected set.  Changes are reported to client watchers and used internally to update 
 * reference counts.  
 */
class DeepWatch(val root:Syncable, val fn:ChangeFn, val watchClass:Any) {
  private val connectedSet = mutable.Map[Syncable, Int]()  // tracked syncables and their reference counts
  val debugId = DeepWatchDebug.nextDebugId()
  var disabled = false
 
  init()
  
  /** start by watching the root object and it's recursive references */
  private def init() = {
    addedRef(root)	// add the whole reference tree to the connected set, and observes all elements.
    //    Console println "DeepWatch started: "
    //    printConnectedSet
    //    verifyConnectedSet
  }

  /** print for debugging */
  def printConnectedSet {
    for ((obj, count) <- connectedSet) {
      Console println "  " + obj + ":" + count
    }
  }

  /** debug verification of the connected set for consistency */
  def verifyConnectedSet {
    /* LATER make this recalculate the entire set and compare with the
     * dynamically maintained one */
    for ((obj, count) <- connectedSet) {
      assert(count > 0)
    }
  }
  
  def disable() {
    Observers.unwatchAll(this)
    disabled = true
  }
  
  /** called when on one of the objects we're watching has changed */
  private def handleChanged(change:ChangeDescription):Unit = {	    
    change match {
      case propChange:PropertyChange => 	
        handlePropChange(propChange)
      case memberChange:MembershipChange => 
        handleMemberChange(memberChange)
      case clearChange:ClearChange =>
        handleClearChange(clearChange)
      case m:MoveChange =>
    }
    
    // tell the client observer about this about this change
    fn(change)
    
    //    Console println "deepWatch changed: " + change
    //    printConnectedSet
    verifyConnectedSet
  }
  
  private def handlePropChange(propChange:PropertyChange) {
	// update ref counts for old value
    propChange.oldValue match {
      case old:SyncableId => removedRef(old)
      case _ =>
    }

	// update ref count for new value
    propChange.newValue match {
      case newBranch:SyncableId => 
        addedRef(newBranch)
      case _ =>
    }
  }
  
  /** update references in response to change to collection membership */
  private def handleMemberChange(memberChange:MembershipChange) {
    if (memberChange.newValue != null) {
      memberChange.newValue.asInstanceOf[SyncableId].target foreach {addedRef(_)}
    }
    if (memberChange.oldValue != null) {
      memberChange.oldValue.asInstanceOf[SyncableId].target foreach removedRef
    }
  }
  
  /** update references in response to clearing collection membership */
  private def handleClearChange(clearChange:ClearChange) {
    clearChange.members foreach removedRef
  }
   
  /** decrement reference counter for this object and every Observable it 
   * references.  If any object's reference counter drops to zero,
   * remove the object from the connectedSet of objects we observe */
  private def removedRef(ref:SyncableId) {
    SyncManager.get(ref) foreach removedRef
  }
  
  private def removedRef(target:Syncable) {
    connectedSet get target match {
      case Some(count) if count > 1 => connectedSet + (target -> (count -1))
      case Some(count) if count == 1 => removeObj(target)
      case Some(count) => Log.error("BUG? count of ref: " + target+ " is: " + count)
      case None => Log.error("BUG? removeDeep" + target + "not found in connectedSet")
    }    
  }
  
  private def removeObj(obj:Syncable) {              
    // stop watching this object
    connectedSet - obj
    Observers.unwatch(obj, this)
 
    // generate a membership change to the connected set, and tell overyone
    val change = new UnwatchChange(root.asInstanceOf[Syncable].fullId, obj.asInstanceOf[Syncable].fullId)
    fn(change)
 
    // update reference counts 
    for (ref <- observableReferences(obj)) 
      removedRef(ref.fullId)
  }
      
  /* increment reference counter for this object and every Observable it
   * references.  Make sure this object and every observable it references are
   * in the connectedSet of objects we observe. */
  private def addedRef(target:Syncable) {        
    connectedSet get target match {
      case Some(count) => connectedSet + (target -> (count + 1))
      case None => addObj(target)
    }
  }
  
  private def addedRef(ref:SyncableId) {
    SyncManager.get(ref) foreach addedRef
  }
    
  private def addObj(obj:Syncable) {
    // start watching this object
    connectedSet + (obj -> 1); 			 
    Observers.watch(obj, handleChanged, this)   
    
    // generate a membership change to the connected set, and tell overyone
    val change = new WatchChange(root.fullId, obj.fullId, this)
    fn(change)
    
    for (ref <- observableReferences(obj)) 
      addedRef(ref)
  }
   
  
  override def finalize {  // LATER: consider some kind of loan wrapper approach
    disable()
  }
  
  override def toString:String = "DeepWatch #" + debugId
}

/*
 * LATER add cycle detection to garbage collector.  Perhaps simply trace from the root now and then?
 * For a fancier approach, see:  http://www.research.ibm.com/people/d/dfb/papers/Paz05Efficient.pdf
 * 
*/