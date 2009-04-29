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
import com.digiting.sync.aspects.AspectObservation
import com.digiting.sync.aspects.ObserveListener
import com.digiting.sync.aspects.Observable
import com.digiting.util._

object Observation {
  abstract class ChangeDescription(val target:Observable)

  case class PropertyChange(changed:Observable, property:String, val newValue:Any, val oldValue:Any) 
        extends ChangeDescription(changed){
    override def toString = (target + "." + property + " = " + newValue + " was:" + oldValue)
  }
        
  case class WatchChange(val root:Observable, val newValue:Observable) extends ChangeDescription(root)
  case class UnwatchChange(val root:Observable, val oldValue:Observable) extends ChangeDescription(root)
  
  abstract class MembershipChange(target:Observable, val operation:String, 
                                  val newValue:Any, val oldValue:Any) 
        extends ChangeDescription(target) {
    def operationTarget:Any
    override def toString = (target + "+=" + newValue + " -=" + oldValue)
  }
        
  
  case class PutChange(changed:Observable, newVal:Any) extends MembershipChange(changed, "put", newVal, null) {
    override def operationTarget = newVal
  }
  
  case class RemoveChange(changed:Observable, oldVal:Any) extends MembershipChange(changed, "remove", null, oldVal) {
    override def operationTarget = oldVal
  }
  
      
  case class Watch(changed:Change, watchClass:Any) 
  type Change = (Observable, ChangeDescription)=>Unit

  private var watchers = new MultiMap[Observable, Watch]
  // watch one observable
  private val allWatchers = mutable.Set[Watch]()	// watches every observable!  (get rid of this?)
  private val deepWatches = new mutable.HashMap[Observable, DeepWatch]()	// watch a reference tree of observables

  private object AspectListener extends ObserveListener {
    def change(target:Any, property:String, newValue:Any, oldValue:Any) = {
      Observation.notify(target.asInstanceOf[Observable], property, newValue, oldValue)
    }
  }    
  // listen for model object modifications found from the Aspect compiled Observable objects 
  AspectObservation.registerListener(AspectListener)
  
  /* reset all watches -- useful for testing from a clean slate */
  def reset() {
    watchers.clear()
    allWatchers.clear()
    deepWatches.clear()
  }
  
  /* notify observers that we've changed */
  def notify(obj:Observable, property:String, newValue:Any, oldValue:Any):Unit = {
    if (!property.startsWith("sync$")) {
      val changes = new PropertyChange(obj, property, newValue, oldValue)
//		  Console println "Observation.notify()" + changes
      notify(changes)
    }
  }
  
  /* notify observers that we've changed */
  def notify(changes:ChangeDescription):Unit = {
      watchers.foreachValue(changes.target) {watch =>  
        watch.changed(changes.target, changes)
      }
        
	  for (watch <- allWatchers) {
	    watch.changed(changes.target, changes)
	  }    
  }
    
  /* Register a function to be called when an object is changed.  */
  def watch(obj:Observable, fn:Change, watchClass:Any) {
    watchers + (obj, Watch(fn, watchClass))
  }
  
  /* Register a function to be called when the object or any referenced object is changed 
   * 
   * @param root  	  root of the branch of Observable objects to be watched
   * @param fn          function called on each change
   * @param watchClass  names this watch, which enables removing the watch by name
   */
  def watchDeep(root:Observable, fn:Change, watchClass:Any) {
    val deepWatch = deepWatches get root match {
      case Some(deepWatch) => deepWatch
      case None => new DeepWatch(root)
    }
    deepWatch.watch(fn, watchClass)
  }
  
  /* unregister all watch functions registered with a given watchClass
   * on a given object */
  def unwatch(obj:Observable, watchClass:Any) {
    watchers.removeValues(obj){watch => watch.watchClass == watchClass}
  }
  
  /* unregister all watch functions registered with a given watchClass
   * on any object (or on registered with watchAll) */
  def unwatchAll(watchClass:Any) {
    for (obj <- watchers.keys) {        
      unwatch(obj, watchClass)
    }
    
    // TODO clear deep watchers, clear all watchers
  }
  
  /* watch changes to every observed object */
  def watchAll(fn:Change, watchClass:Any) {
    allWatchers + new Watch(fn, watchClass)
    throw new NotYetImplemented
  }
}

