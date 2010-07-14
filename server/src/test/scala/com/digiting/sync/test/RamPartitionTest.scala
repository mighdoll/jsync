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
import com.digiting.sync.Pickled
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.digiting.sync.syncable.TestNameObj

@RunWith(classOf[JUnitRunner])
class RamPartitionTest extends Spec with ShouldMatchers with SyncFixture {
  
  private def makeTestSeq():SyncableSeq[TestNameObj] = {
    val s = new SyncableSeq[TestNameObj]
    s += new TestNameObj("Gavin")
    s += new TestNameObj("Quinn")
    s
  }
  
  private def makeTestMap():SyncableMap[String, TestNameObj] = {
    val map = new SyncableMap[String,TestNameObj]
    map("e") = new TestNameObj("Elle")
    map("j") = new TestNameObj("Janet")
    map
  }
                                  
  describe("RamPartition") {
    it("should store and modify a simple object") {
      withTestFixture {
        
        val s = withTestPartition {TestNameObj("Oleg")}
        withTestPartition {s.name = "Huck"}        
        withTempContext {
          testPartition.get(s.id.instanceId) match {
            case Some(found:TestNameObj) =>
              found.name should be ("Huck")
            case _ =>
              fail
          }
        }
      }
    }
    it("should store and retrieve a seq") {
      withTestFixture {
        val s = withTestPartition {makeTestSeq()}
        withTempContext {
          testPartition.get(s.id.instanceId) match {
            case Some(found:SyncableSeq[_]) =>
              val seq = found.asInstanceOf[SyncableSeq[TestNameObj]]
              seq.length should be (2)
              seq(0).name should be ("Gavin")
              seq(1).name should be ("Quinn")
            case _ =>
              fail
          }
        }
      }
    }
    
    it("should move an element from a seq") {
      withTestFixture {
        val s = withTestPartition {makeTestSeq()}
        withTestPartition {s.move(0, 1)}
        withTempContext {
          testPartition.get(s.id.instanceId) match {
            case Some(found:SyncableSeq[_]) =>
              val seq = found.asInstanceOf[SyncableSeq[TestNameObj]]
              seq.length should be (2)
              seq(1).name should be ("Gavin")
              seq(0).name should be ("Quinn")
            case _ =>
              fail
          }
        }
      }
    }
    it("should remove an element from a seq") {
      withTestFixture {
        val s = withTestPartition {makeTestSeq()}
        withTestPartition {s.remove(0)}
        withTempContext {
          testPartition.get(s.id.instanceId) match {
            case Some(found:SyncableSeq[_]) =>
              val seq = found.asInstanceOf[SyncableSeq[TestNameObj]]
              seq.length should be (1)
              seq(0).name should be ("Quinn")
            case _ =>
              fail
          }
        }
      }
    }
    
    it("should store and retrieve a set") {
      withTestFixture {
        val s = withTestPartition {new SyncableSet[TestNameObj]}
        withTestPartition {
          s += TestNameObj("Elle")
          s += TestNameObj("Janet")
        }  
        withTempContext {        
          testPartition.get(s.id.instanceId) match {
            case Some(found:SyncableSet[_]) =>
              val set = found.asInstanceOf[SyncableSet[TestNameObj]]
              var foundJanet, foundElle = false
              set.size should be (2)
              for {name <- set} {
                if (name.name == "Elle") 
                  foundElle = true
                else if (name.name == "Janet")
                  foundJanet = true;            
              }
              foundJanet should be (true)
              foundElle should be (true)
            case _ =>
              fail
          }
        }
      }
    }
    
    it("should remove an element from a set") {
      withTestFixture {
        val s = withTestPartition {new SyncableSet[TestNameObj]}
        val beth = withTestPartition {TestNameObj("Beth")}
        withTestPartition {s += beth}
        withTestPartition {s -= beth}
        withTempContext {        
          testPartition.get(s.id.instanceId) match {
            case Some(found:SyncableSet[_]) =>
              val set = found.asInstanceOf[SyncableSet[TestNameObj]]
              set.size should be (0)
            case _ =>
              fail
          }
        }
      }
    }
    
    it("should store and retrieve a map") {
      withTestFixture {
        val s = withTestPartition {makeTestMap()}
        withTempContext {        
          testPartition.get(s.id.instanceId) match {
            case Some(found:SyncableMap[_,_]) =>
              val map = found.asInstanceOf[SyncableMap[String,TestNameObj]]
              map("e").name should be ("Elle")
              map("j").name should be ("Janet")
            case _ =>
              fail
          }
        }
      }
    }
    
    it("should remove an element from a map") {
      withTestFixture {
        val s = withTestPartition {makeTestMap()}
        withTestPartition {s -= ("e")}
        withTempContext {        
          testPartition.get(s.id.instanceId) match {
            case Some(found:SyncableMap[_,_]) =>
              val map = found.asInstanceOf[SyncableMap[String,TestNameObj]]
              map get ("e") should be (None)
              map("j").name should be ("Janet")
            case _ =>
              fail
          }
        }
      }
    }
    
  }
}

