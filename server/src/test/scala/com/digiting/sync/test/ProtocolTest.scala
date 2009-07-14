package com.digiting.sync.test
import com.jteigen.scalatest.JUnit4Runner
import org.junit.runner.RunWith
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import com.digiting.sync.syncable._
import ObserveUtil._

@RunWith(classOf[JUnit4Runner])
class ProtocolTest extends Spec with ShouldMatchers {
  describe("JsonSync") {    
    it("should support subscribe on a simple object") {
      import actors._
      import SendBuffer._
      import actors.Actor._
      val connection = new Connection("ProtocolTest")
      val input = """ [
	    {"#transaction":0},
	    {"#start":true},
	    {"kind":"$sync.set",
	     "id":"#subscriptions"},
	    {"kind":"$sync.subscription",
	     "id":"Browser-0",
	     "name":"oneName",
	     "inPartition":"test",
	     "root":null}
	    ] """
      for (message <- ParseMessage.parse(input)) {        
        connection.receiver ! Receiver.ReceiveMessage(message)
	
        val pending = connection.sendBuffer !? Take()
        var reply = ""
        pending match {
          case Pending(json) =>
            reply = json
          case msg =>
            Console println "unexpected message: " + msg
        }
      
        assert (reply contains "emmett")
      }
      resetObserveTest()
    }
  }
}
