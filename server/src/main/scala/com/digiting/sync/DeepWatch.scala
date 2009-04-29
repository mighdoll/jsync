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
import Observation._

/* watch all observable objects referenced by this one, recursively */
class DeepWatch(root:Observable) {
  private val connectedSet = mutable.Map[AnyRef, Int]()  // tracked observables and their reference counts
  private val watches = mutable.Set[Watch]()
 
  init()
  
  // start by watching the root object
  def init() = {
    addedRef(root)
//    Console println "DeepWatch started: "
//    printConnectedSet
//    verifyConnectedSet
  }
  
  /* register a callback when any object in the set is changed */
  def watch(fn:Change, watchClass:Any) {
    watches + new Watch(fn, watchClass)
  }
  
  def printConnectedSet {
    for ((obj, count) <- connectedSet) {
      Console println "  " + obj + ":" + count
    }
  }
  
  def verifyConnectedSet {
    /* LATER make this recalculate the entire set and compare with the
     * dynamically maintained one */
    for ((obj, count) <- connectedSet) {
      assert(count > 0)
    }
  }
  
  /* handle change on one of the objects we're watching */
  private def handleChanged(obj:Observable, change:ChangeDescription):Unit = {	    
    change match {
      case propChange:PropertyChange => {
	    // update ref counts for old value 
        propChange.oldValue match {
          case old:Observable => removedRef(old)
          case _ =>
        }
	    
	    // update ref count for new value 
	    propChange.newValue match {
	      case newBranch:Observable => addedRef(newBranch)
          case _ =>
        }
      }       
      case membershipChange:MembershipChange => throw new NotYetImplemented
    }
    // tell the client code about this about this change
    watches foreach (_.changed(obj, change))
    
//    Console println "deepWatch changed: " + change
//    printConnectedSet
    verifyConnectedSet
  }
   
  /* decrement reference counter for this object and every Observable it 
   * references.  If any object's reference counter drops to zero,
   * remove the object from the connectedSet of objects we observe */
  private def removedRef(ref:Observable) {
	  connectedSet get ref match {
	    case Some(count) if count > 1 => connectedSet + (ref -> (count -1))
	    case Some(count) if count == 1 => removeObj(ref)
	    case Some(count) => Log.error("BUG? count of ref: " + ref+ " is: " + count)
	    case None => Log.error("BUG? removeDeep" + ref + "not found in connectedSet")              
	  }
  }
  
  private def removeObj(obj:Observable) {              
    // stop watching this object
	connectedSet - obj
	Observation.unwatch(obj, this)
 
	// tell everyone
	val change = new UnwatchChange(root, obj)
	watches foreach (_.changed(root,change))
 
    // update reference counts 
    for (ref <- observableRefs(obj)) 
      removedRef(ref)
  }
      
  /* increment reference counter for this object and every Observable it
   * references.  Make sure this object and every observable it references are
   * in the connectedSet of objects we observe. */
  private def addedRef(ref:Observable) {        
    connectedSet get ref match {
      case Some(count) => connectedSet + (ref -> (count + 1))
      case None => addObj(ref)
    }
  }
    
  private def addObj(obj:Observable) {
    // start watching this object
    connectedSet + (obj -> 1); 			 
    Observation.watch(obj, handleChanged, this)   
    
    // tell everyone 
    val change = new WatchChange(root, obj)
    watches foreach (_.changed(root,change))
    
    for (ref <- observableRefs(obj)) 
      addedRef(ref)      
  }
  

  /* Run a function on every reference to an Observable object in a branch of Observable
   * objects.  
   * 
   * If two references refer to the same Observable object, the function will be called twice. 
   * 
   * @param branch  root of the tree
   * @param fn      function called with the target of each reference
   */
  private def observableReferencesDeep(branch:Observable)(fn:Observable=>Unit) = {
    // start with every (unique) referenced observable element in the branch
    // and then walk every (non-unique) reference to an observable element
    for (elem <- observablesDeep(branch);
         ref <- observableRefs(elem)) { 
      fn(ref)
    }
  }

  /* collect every Observable object referenced by a root object or
   * any of the root object's Observable descendants.  The root object is included
   * in the set.
   * 
   * @param set     accumulates the collection of referenced Observerables
   * @param branch  root object
   */
  private def observablesDeep(branch:Observable):Seq[Observable] =  {        
    
    /* recursive collector */
    def collectObservables(branch:Observable, set:mutable.Set[Observable]):Unit = {
      set + branch
      for (ref <- observableRefs(branch)) {
        if (!(set contains ref))
          collectObservables(ref, set)
      } 
    }
 
    val accumulate = mutable.Set[Observable]()
    collectObservables(branch, accumulate)
    accumulate.toSeq      
  }
  
  /* Collect every reference to an Observable object directly from a given object
   * 
   * @param base  object instance to scan for references
   */
  private def observableRefs(obj:AnyRef):Seq[Observable] = {
    val refs = new mutable.ListBuffer[Observable]()
    Accessor.references(obj) foreach {_ match {
      case ref:Observable => refs + ref
      case _ =>
    }}
    refs.toSeq
  }      
  
  override def finalize {  // LATER: consider some kind of loan wrapper approach
    Observation.unwatchAll(this)
  }	  
}