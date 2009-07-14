package com.digiting.sync.test

import com.jteigen.scalatest.JUnit4Runner
import org.junit.runner.RunWith
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.Spec
import org.scalatest.SuperSuite
import com.digiting.sync._
import com.digiting.sync.aspects.Observable
import com.digiting.sync.syncable._
import com.digiting.util._
import collection._
import SyncManager.NewSyncableIdentity

import ObserveUtil._

@RunWith(classOf[JUnit4Runner])
class SyncableTest extends Spec with ShouldMatchers {
  describe("Syncable") {
    it("should allow setting an id") {
      val obj = 
        SyncManager.setNextId.withValue(NewSyncableIdentity("foo", SyncManager.currentPartition.value)) {
    	  new SyncableSet
      	}
      obj.id should be ("foo")
      SyncManager.get(SyncManager.currentPartition.value.partitionId, "foo") should be (Some(obj))
      resetObserveTest()
    }        
  }
}
