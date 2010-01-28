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
import com.digiting.sync.aspects.Observable
import com.digiting.util._
import com.digiting.sync.aspects.AspectObservation
import com.digiting.sync.aspects.ObserveListener
import scala.util.DynamicVariable
import scala.collection._
import scala.util.matching.Regex

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
object Observers extends LogHelper { 
  val log = Logger("Observers")
  /** called when a change happens */
  type DataChangeFn = (DataChange)=>Unit   // Consider a listener object...
  type WatchChangeFn = (WatchChange)=>Unit   // Consider a listener object...
  
  /** an observer watching for changes.  
   * @param watchClass  is a caller specified indentifer so that the caller can delete single or multiple watches by identifer
   */
  case class Watcher(changed:DataChangeFn, watchClass:Any)    
  case class Notification(watcher:Watcher, change:ChangeDescription)
  
  var currentMutator = new DynamicVariable("server") 						// 'source' of current changes, tagged onto all observations
  private var watchers = new MultiMap[Syncable, Watcher]  				// watch one observable  CONSIDER can we use std library MultiMap?
  private val deepWatches = new MultiMap[Syncable, DeepWatch]()		// watch a connected set of observables
  private var holdNotify = new DynamicVariable[Option[mutable.ListBuffer[Notification]]](None)
  
  // listen for model object modifications found from the AspectJ enhanced Observable objects 
  private object AspectListener extends ObserveListener {    
    def change(target:Any, property:String, newValue:Any, oldValue:Any) = {
      if (!SyncableInfo.isReserved(property)) {
        val targetId = target.asInstanceOf[Syncable].fullId
        val change = PropertyChange(targetId, property, SyncableValue.convert(newValue), 
          SyncableValue.convert(oldValue))
        Observers.notify(change)
      }
    }
  }
  
  AspectObservation.registerListener(AspectListener)
    
  /** reset all watches -- useful for testing from a clean slate */
  def reset() {
    watchers.clear()
    deepWatches.clear()
  }
    
  /** notify observers of the change */
  def notify(change:DataChange):Unit = {
    SyncManager.get(change.target) orElse {
      err("can't find target of change: %s", change.toString) 
    } foreach { target =>      
      holdNotify.value match {
        case Some(paused) => 
          watchers.foreachValue(target) {watch =>  
            val notify = Notification(watch, change)
            log.trace("queing notification: %s", notify)
            paused += notify
          }
        case _ =>
    	    watchers.foreachValue(target) {watch =>  
    	      watch.changed(change)
          }        
      }
    }
  }
  
  def withNoNotice[T](fn: => T):T = {
    // just use a pause buffer and throw away the contents 
    holdNotify.withValue(Some(new mutable.ListBuffer[Notification])) {
      log.trace("notices disabled")
      val result = fn
      log.trace("notices re-enabled")
      result
    }
  }

    
  /* Register a function to be called when an object is changed.  */
  def watch(obj:Syncable, fn:DataChangeFn, watchClass:Any) {
    log.trace("watch: %s by %s", obj, watchClass)
    watchers + (obj, Watcher(fn, watchClass))
  }
  
  /* Register a function to be called when the object or any referenced object is changed 
   * 
   * @param root        root of the branch of Observable objects to be watched
   * @param fn          function called on each data change
   * @param fn          function called on changes to the watched set
   * @param watchClass  names this watch, which enables removing the watch by name
   */
  def watchDeep(root:Syncable, fn:DataChangeFn, watchFn:WatchChangeFn, watchClass:Any):DeepWatch = {
    val deepWatch = new DeepWatch(root, fn, watchFn, watchClass)
    deepWatches + (root, deepWatch)
    deepWatch
  }
  
  /** unregister all watch functions registered with a given watchClass
   * on a given object */  
  def unwatch(obj:Syncable, watchClass:Any) {
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
  
  def withMutator[T](mutator:String)(fn: =>T):T = {
    currentMutator.withValue(mutator) {
      fn
    }
  }
  
  def pauseNotification[T](fn: =>T):T = {    
    log.trace("pausing notification")    
    val origHold = holdNotify.value
    holdNotify.value = Some(new mutable.ListBuffer[Notification])
    val (result, pausedBuffer) = 
      try {
        (fn, holdNotify.value) // note that the fn could change the holdNotify value (e.g via releasePaused), so we refetch
      } finally {
        holdNotify.value = origHold
      }    
    
    log.trace("releasing paused notifications")
    pausedBuffer match {	
      case Some(paused) => 
        paused foreach { notification =>
          log.trace("pauseNotification, releasing: %s : %s", notification.watcher.watchClass, notification.change)
          notification.watcher.changed(notification.change)
        }
      case _ =>
        log.warning("pauseNotification, that's odd.  where'd the pause buffer go?")
    }
    log.trace("all paused notifications released")
    result
  }
  
  def releasePaused(matchFn:(Any)=>Boolean) {
    // accumulate notifiations that we're leaking out of the pause buffer
    val releasing = new mutable.ListBuffer[Notification]
 
    // release notifications that match 	                                      
    holdNotify.value map {held =>
      for {
        notification <- held
        if matchFn(notification.watcher.watchClass)
      } {        
        releasing += notification
        log.trace("releasePaused, releasing: %s : %s", notification.watcher.watchClass, notification.change)
        notification.watcher.changed(notification.change)
      }
      
      // remove released notifications from pause buffer
      val remainder = new mutable.ListBuffer[Notification]
      remainder ++= (held.toList -- releasing.toList)
      holdNotify.value = Some(remainder)
    }
  }  
}

