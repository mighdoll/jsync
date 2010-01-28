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
package com.digiting.sync.test
import com.digiting.sync.syncable._
import ObserveUtil._
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import net.lag.logging.Logger

@RunWith(classOf[JUnitRunner])
class ObservationTest extends Spec with ShouldMatchers {
  val log = Logger("ObservationTest")
  
  describe("Observation.watch") {
    it("should notice property change on an object") {
      withTestEnvironment {
        val obj = new TestNameObj
        Observers.watch(obj, changed, this)
        obj.name = "MiloJ"  // should trigger an observable change

        assert (changes.length == 1)
      }
    }
    
    it("it should be able to defer notification") {
      withTestEnvironment {
        val ref = new TestRefObj
        Observers.watch(ref, changed, "test")
        Observers.pauseNotification {
          ref.ref = ref
          changes.size should be (0)
        }
        changes.size should be (1)
      }
    }

  }
  
  describe("Observation.watchDeep") {
    it("should stop watching objects that are no longer referenced") {
      withTestEnvironment {
        val root = new TestRefObj
        val one = new TestNameObj
        root.ref = one
        Observers.watchDeep(root, changed, changed, this)	// generates two watch change events
        root.ref = null	// generates one prop change, and one unwatch change
        log.trace("changes: \n%s", changes mkString("\n"))
        changes.size should be (4)	
        one.name = "one"	// should be outside the watched tree
        changes.size should be (4)
      }
    }
    
    it ("should see changes in a reference chain") {
      withTestEnvironment {
        val obj = new TestRefObj()
        val obj2 = new TestRefObj()
        val obj3 = new TestRefObj()
        obj.ref = obj2			// two watch changes

        Observers.watchDeep(obj, changed, changed, this)
        obj.ref = null			// unwatch + property change
        changes.size should be (4)  
        obj.ref = obj2			// watch + property change
        changes.size should be (6)    
        obj2.ref = obj3			// watch + property change
        changes.size should be (8)   
    }
  }

    it("should see changes in a tree") {
      withTestEnvironment {
        val root = new TestRefObj
        Observers.watchDeep(root, changed, changed, this)	// watch
        val branch = new TestTwoRefsObj			
        val one,two = new TestNameObj
        root.ref = branch					// watch + prop change
        changes.size should be (3)
        branch.ref1 = one							// watch + property change
        changes.size should be (5)
        branch.ref2 = two							// watch + property change
        changes.size should be (7)
        branch.ref2 = null						// unwatch + property change
        changes.size should be (9)
        branch.ref2 = one							// property change
        changes.size should be (10)

        var watchCount = 0
        var propCount = 0
        var unwatchCount = 0
        for (change <- changes) {
          change match {
            case PropertyChange(_,_,_,_) =>
              propCount += 1
            case BeginWatch(_,_,_) =>
              watchCount += 1
            case EndWatch(_,_) =>
              unwatchCount += 1
            case _ =>
          }
        }
        watchCount should be (4)
        propCount should be (5)
        unwatchCount should be (1)

        one.name = "one"	// property change (inside the tree)
        changes.size should be (11)
        two.name = "two"  // no chagne (should be outside the tree now)
        changes.size should be (11)
      }
    }

    it("should find members referenced from a collection") {
      withTestEnvironment {
        val set = new SyncableSet[Syncable]
        val name = new TestNameObj 
        set + name
        Observers.watchDeep(set, changed, changed, "test")	// two watch changes, one BaseMembership
        name.name = "Ben"	// one property change
        Console println changes.mkString("\n")        
        changes.size should be (4)
      }
    }
    
  }
}
