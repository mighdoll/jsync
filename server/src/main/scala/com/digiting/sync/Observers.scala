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
import com.digiting.util._
import com.digiting.sync.aspects.AspectObservation
import com.digiting.sync.aspects.ObserveListener
import scala.util.DynamicVariable

/**
 * Change tracking utility for Observable objects and collections.
 * 
 * CRUD changes are reported as ChangeDescriptions by the objects and collections with notify().
 * Interested observers can register for changes via watch() or deepWatch().  deepWatch(target)
 * watches all objects referened by target.  Observers cancel their watches via unwatch()
 * 
 * CONSIDER contention from threads in deciding to decompose into objects..
 * 
 * CONSIDER make this an actor, it's called from multiple threads..?
 * alternately CONSIDER decentralizing observation to go onto the objects instead
 * SCALA -- consider api observation.  e.g. should watch() send an actor message or provide a callback?
 */
object Observers { 
  /** called when a change happens */
  type ChangeFn = (ChangeDescription)=>Unit		// Consider a listener object...
  
  /** an observer watching for changes.  
   * @param watchClass  is a caller specified indentifer so that the caller can delete single or multiple watches by identifer
   */
  case class Watcher(changed:ChangeFn, watchClass:Any)    
  
  var currentMutator = new DynamicVariable("server") 						// 'source' of current changes, tagged onto all observations
  private var watchers = new MultiMap[Observable, Watcher]  				// watch one observable
  private val deepWatches = new MultiMap[Observable, DeepWatch]()		// watch a connected set of observables
  
  // listen for model object modifications found from the AspectJ enhanced Observable objects 
  private object AspectListener extends ObserveListener {
    def change(target:Any, property:String, newValue:Any, oldValue:Any) = {
      Observers.notify(PropertyChange(target.asInstanceOf[Observable], property, newValue, oldValue))
    }
  }    
  AspectObservation.registerListener(AspectListener)
    
  /** reset all watches -- useful for testing from a clean slate */
  def reset() {
    watchers.clear()
    deepWatches.clear()
  }
    
  /** notify observers of the change */
  def notify(changes:ChangeDescription):Unit = {
    watchers.foreachValue(changes.target) {watch =>  
//      Console println("Observers.notify: " + watch.watchClass + " : " + changes)
      watch.changed(changes)
    }
  }
    
  /* Register a function to be called when an object is changed.  */
  def watch(obj:Observable, fn:ChangeFn, watchClass:Any) {
//    Console println("Observers.watch: " + obj + " by " + watchClass)
    watchers + (obj, Watcher(fn, watchClass))
  }
  
  /* Register a function to be called when the object or any referenced object is changed 
   * 
   * @param root  	  root of the branch of Observable objects to be watched
   * @param fn          function called on each change
   * @param watchClass  names this watch, which enables removing the watch by name
   */
  def watchDeep(root:Observable, fn:ChangeFn, watchClass:Any) {
    deepWatches + (root, new DeepWatch(root, fn, watchClass))
  }
  
  /** unregister all watch functions registered with a given watchClass
   * on a given object */
  def unwatch(obj:Observable, watchClass:Any) {
    watchers.removeValues(obj) { watch => 
      watch.watchClass == watchClass
    }
    deepWatches.removeValues(obj)  { deepWatch =>
      deepWatch.watchClass == watchClass
    }
  }
  
  /** unregister all watch functions registered with a given watchClass */
  def unwatchAll(watchClass:Any) {
    for (obj <- watchers.keys) {        
      unwatch(obj, watchClass)
    }
    
    for (obj <- deepWatches.keys) {        
      unwatch(obj, watchClass)
    }
  }
  
}

