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

import com.digiting.sync._
import com.digiting.sync.aspects.Observable
import com.digiting.sync.syncable._
import com.digiting.sync.Observation._  
import com.digiting.util._

import collection._

object SyncTest {
  def main(args:Array[String]) = {}
  
  val changes = new mutable.ListBuffer[ChangeDescription]()
  def changed(ob:Observable, change:ChangeDescription) {
    changes + change   
  }

//  testJsonMessage
  testWatchTree
  testMultiMap
  testWatchDeep
  testWatchSimpleGC
  testAccessorReferences
  testWatch
  testClassAccessor
  testSyncableSetId
  testSubscribe
  exit
  
  
  def testClassAccessor {    
    val obj = new TestNameObj()
    obj.name = "Bruce"
    val a = SyncableAccessor.get(classOf[TestNameObj])
    a.set(obj, "name", "Fred")
    assert (obj.name == "Fred")
  }
  
  def testSyncableSetId = {
    val s = new SyncableSet
    s.setId("foo")
	assert (s.id == "foo")
  }
  
  def resetObserveTest() {
    Observation.unwatchAll(this)    
    Observation.reset()   
    changes.clear()
  }
  
  def testWatch = {
    val obj = new TestNameObj    
    Observation.watch(obj, changed, this)
    obj.name = "MiloJ"  // should trigger an observable change
    
    assert (changes.length == 1)
    resetObserveTest()
  }
  
  def testAccessorReferences {
    val obj = new TestRefObj()
    val obj2 = new TestRefObj()
    obj.reference = obj2
    
    // verify internal accessor is built correctly
    val accessor = SyncableAccessor.get(obj.getClass)
    val refs = accessor.references(obj).toList
    
    assert (refs.length == 1)
    assert (refs.head == obj2)
  }

  def testWatchDeep {
    val obj = new TestRefObj()
    val obj2 = new TestRefObj()
    val obj3 = new TestRefObj()
    obj.reference = obj2

    Observation.watchDeep(obj, changed, this)
    obj.reference = null
    assert (changes.size == 2) // membership change + property change
    obj.reference = obj2
    assert (changes.size == 4)  // membership change + property change
    obj2.reference = obj3
    assert (changes.size == 6)	// membership change + property change
    resetObserveTest()
  }
  
  def testWatchSimpleGC {
    val root = new TestRefObj
    val one = new TestNameObj
    root.reference = one
    Observation.watchDeep(root, changed, this)
    root.reference = null
    assert (changes.size == 2)
    one.name = "one"	// should be outside the watched tree
    assert (changes.size == 2)
    
    resetObserveTest()
  }
  
  def testWatchTree {
    val root = new TestRefObj
    Observation.watchDeep(root, changed, this)
    val branch = new TestTwoRefsObj
    val one,two = new TestNameObj
    root.reference = branch
    assert(changes.size == 2)
    branch.ref1 = one
    assert(changes.size == 4)    
    branch.ref2 = two
    assert(changes.size == 6)
    branch.ref2 = null
    assert(changes.size == 8)
    branch.ref2 = one
    assert(changes.size == 9)

    
    var watchCount = 0
    var propCount = 0
    var unwatchCount = 0
    for (change <- changes) {
      change match {
        case PropertyChange(_,_,_,_) => 
          propCount += 1
        case WatchChange(_,_) => 
          watchCount += 1
        case UnwatchChange(_,_) => 
          unwatchCount += 1
        case _ =>
      }
    }
    assert(watchCount == 3)
    assert(propCount == 5)
    assert(unwatchCount == 1)

    one.name = "one"
    assert(changes.size == 10)
    two.name = "two"  // should be outside the tree now
    assert(changes.size == 10)
    
    resetObserveTest
  }
  
  def testMultiMap {
    val map = new MultiMap[Int,String]
    map + (1,"foo")
    map - (2,"")
    map + (1,"foo")
    map - (1,"bar")
    map + (1,"cal")
    assert(!map.isEmpty)
    map - (1,"foo")
    assert(!map.isEmpty)    
    map - (1,"cal")
    assert(map.isEmpty)    
  }
  
  def testJsonMessage {
    import JsonMessageControl._
    val syncs = ImmutableJsonMap("id" -> "test-2", "name" -> "Sandi" ) :: Nil
    val editList = ImmutableJsonMap("put" -> "test-2") :: Nil
    val edits = ImmutableJsonMap("#edit" -> "test-1", "#edits" -> editList) :: Nil
    val message = new JsonMessage(Init, 0, edits, syncs)
    message
    val json = message.toJson
      
    Console println json
  }

  def testSubscribe {
    import JsonConnection._
    import actors._
    import JsonSendBuffer._
    import actors.Actor._
    val connection = new JsonConnection
	  val input = """ [
	    {"#transaction":0},
	    {"#reconnect":false},
	    {"kind":"$sync.set",
	     "id":"#subscriptions"},
	    {"kind":"$sync.subscription",
	     "id":"Browser-0",
	     "name":"$sync/test/oneName",
	     "root":null}
	    ] """
	connection ! new ReceiveJsonText(input)

    val pending = connection.sendBuffer !? Take()
    var reply = ""
    pending match {
      case Pending(json) =>
	    reply = json
//	    Console println "testSubscribe: " + json
      case msg => 
	    Console println "unexpected message: " + msg            
     }
      
     Console println "testSubscribe succeeded"
     Console println reply
     assert (reply contains "emmett")
     resetObserveTest()
   }
}
