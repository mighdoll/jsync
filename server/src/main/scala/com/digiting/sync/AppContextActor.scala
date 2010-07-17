package com.digiting.sync
import scala.actors.Actor
import Actor._
import com.digiting.util._
import Log2._

case class Flush()

trait AppContextActor extends Actor {
	self:AppContext =>
  implicit private lazy val log = logger("AppContextActor")
  start()

  def act {
    loop {
    	react {
        case message:Message => 
          trace2("message received: %s", message)
          clientChanges(message)
        case (partition:Partition, changes:Seq[_]) =>
          trace2("partition changes received: %s %s", partition, changes mkString("\n"))
          partitionChanges(partition, changes.asInstanceOf[Seq[DataChange]])
        case Flush() => // for tests, allows synchronous wait that prior messages have been processed
        	trace2("flush")
          reply("flushed")
        case x =>
          abort2("unexpected message received %s", x.asInstanceOf[AnyRef])
      }
    }
  }
  
  private def clientChanges(message:Message) {
	  ProcessMessage.process(message, this)    
  }
  
  private def partitionChanges(partition:Partition, changes:Seq[DataChange]) {
    applyPartitionChanges(partition, changes)
  }
  
  
}

// SOON, when we switch to 2.8 we can make this a reactor and use typed messages..
