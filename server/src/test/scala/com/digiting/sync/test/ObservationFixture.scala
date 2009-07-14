package com.digiting.sync.test
import collection._


/* LATER: make this an instance (not a global) if we ever want to run tests in parallel.. */
object ObserveUtil {
  val changes = new mutable.ListBuffer[ChangeDescription]()
  def changed(change:ChangeDescription) {
    changes + change
  }

  def resetObserveTest() {
    Observers.unwatchAll(this)
    Observers.reset()
    SyncManager.reset()
    changes.clear()
  }
}
