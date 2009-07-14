package com.digiting.sync.test
import com.jteigen.scalatest.JUnit4Runner
import org.junit.runner.RunWith
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import com.digiting.sync.syncable._
import ObserveUtil._


@RunWith(classOf[JUnit4Runner])
class ParseTest extends Spec with ShouldMatchers {
  describe("JsonSync") {
    it("should convert maps to messages and back") {
      val syncs = ImmutableJsonMap("id" -> "test-2", "name" -> "Sandi" ) :: Nil
      val editList = ImmutableJsonMap("put" -> "test-2") :: Nil
      val edits = ImmutableJsonMap("#edit" -> "test-1", "#edits" -> editList) :: Nil
      val controls = ImmutableJsonMap("#start" -> true) :: Nil
      val message = new Message(0, controls, edits, syncs)
      val json = message.toJson

      val parsed = ParseMessage.parse(json)
      parsed.isDefined should be (true)
      parsed match {
        case Some(mess) => 
          val roundTripJson = mess.toJson
          roundTripJson should be (json)
        case _ =>
      }
      resetObserveTest()
    }
  }      
}
