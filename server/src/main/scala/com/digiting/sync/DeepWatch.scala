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
import net.lag.logging.Logger
import com.digiting.util._
import com.digiting.sync.aspects.Observable
import Observers._
import SyncableAccessor.observableReferences

object DeepWatchDebug {
  var nextId = 99
  def nextDebugId() = {nextId += 1; nextId}
}
/** 
 * Watch all observable objects referenced by this object or any of its connected set of references.  
 * Changes to objects in the connected set are reported to watchers, as are objects added or 
 * removed from the connected set (e.g. an object is removed from the connected set when the
 * last reference to it from the connected set is removed).  
 * 
 * LATER Reference cycles in the connected set should be detected occasionally (currently not at all
 *   so cycles of self referencing objects will continue to be watched).
 *
 * Internally, DeepWatch maintains a reference count for every object in the connected set of objects
 * referenced from the root, and the Observers facility to watch for changes to objects in the
 * connected set.  Changes are reported to client watchers and used internally to update 
 * reference counts.  
 */
class DeepWatch(val root:Syncable, val app:AppContext, val fn:DataChangeFn, val watchFn:WatchChangeFn, val watchClass:Any) extends LogHelper {
  val log = Logger("DeepWatch")
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
  private def handleChanged(change:DataChange):Unit = {	    
    change match {
      case propChange:PropertyChange => 	
        handlePropChange(propChange)
      case memberChange:MembershipChange => 
        handleMemberChange(memberChange)
      case clearChange:ClearChange =>
        handleClearChange(clearChange)
      case m:MoveChange =>
      case put:PutMapChange =>
        put.oldValue map {
          app.withGetId(_) {removedRef}
        }
        app.withGetId(put.newValue) {addedRef}
      case remove:RemoveMapChange =>
        app.withGetId(remove.oldValue) {removedRef}
      case deleted:DeletedChange =>
        NYI()
      case created:CreatedChange =>        
    }
    
    // tell the client observer about this about this change
    fn(change)
    
    //    Console println "deepWatch changed: " + change
    //    printConnectedSet
    verifyConnectedSet
  }
  
  private def handlePropChange(propChange:PropertyChange) {
	// update ref counts for old value
    propChange.oldValue.value match {
      case old:SyncableId => 
        app.withGetId(old) {removedRef}
      case _ =>
    }

	// update ref count for new value
    propChange.newValue.value match {
      case newBranch:SyncableId => 
        app.withGetId(newBranch) {addedRef}
      case _ =>
    }
  }
  
  /** update references in response to change to collection membership */
  private def handleMemberChange(memberChange:MembershipChange) {
    if (memberChange.newValue != null) {
      memberChange.newValue.target foreach addedRef // CONSIDER what if target isn't found?
    }
    if (memberChange.oldValue != null) {
      memberChange.oldValue.target foreach removedRef
    }
  }
  
  /** update references in response to clearing collection membership */
  private def handleClearChange(clearChange:ClearChange) {
    clearChange.members foreach {app.withGetId(_){removedRef}}
  }
  
  /** decrement reference counter for this object and every Observable it 
   * references.  If any object's reference counter drops to zero,
   * remove the object from the connectedSet of objects we observe */    
  private def removedRef(target:Syncable) {
    connectedSet get target match {
      case Some(count) if count > 1 => connectedSet + (target -> (count -1))
      case Some(count) if count == 1 => removeObj(target)
      case Some(count) => err("BUG? count of ref: " + target+ " is: " + count)
      case None => err("BUG? removeDeep" + target + "not found in connectedSet")
    }    
  }
  
  private def removeObj(obj:Syncable) {              
    // stop watching this object
    connectedSet - obj
    Observers.unwatch(obj, this)
 
    watchFn(EndWatch(root.id, obj.id, this))
 
    // update reference counts 
    for (ref <- observableReferences(obj)) 
      removedRef(ref)
  }
      
  /* increment reference counter for this object and every Observable it
   * references.  Make sure this object and every observable it references are
   * in the connectedSet of objects we observe. */
  private def addedRef(target:Syncable) {        
    log.trace("%d addedRef: %s", debugId, target)
    connectedSet get target match {
      case Some(count) => connectedSet + (target -> (count + 1))
      case None => addObj(target)
    }
  }

  
  private def addObj(obj:Syncable) {
    log.trace("%d addObj: %s", debugId, obj)
    // start watching this object
    connectedSet + (obj -> 1); 			 
    Observers.watch(obj, this, handleChanged)   
    
    watchFn(BeginWatch(root.id, obj.id, this))
    obj match {
      case collection:SyncableCollection =>
        val elements = collection.syncableElementIds
        if (!elements.isEmpty)
          watchFn(BaseMembership(collection.id, elements, this))
      case _ =>                                           
    }
    
    for (ref <- observableReferences(obj)) 
      addedRef(ref)
  }
   
  
  override def finalize {  // ick!! LATER: consider some kind of loan wrapper approach
    disable()
  }
  
  override def toString:String = "DeepWatch #" + debugId
}

/*
 * LATER add cycle detection to garbage collector.  Perhaps simply trace from the root now and then?
 * For a fancier approach, see:  http://www.research.ibm.com/people/d/dfb/papers/Paz05Efficient.pdf
 * 
*/