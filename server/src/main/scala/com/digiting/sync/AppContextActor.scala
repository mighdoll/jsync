package com.digiting.sync
import scala.actors.Actor
import Actor._
import com.digiting.util._
import Log2._

case class Flush()

trait AppContextActor extends Actor {
	app:AppContext =>
  
  implicit private lazy val log = logger("AppContextActor")
  start()

  def act {
    loop {
    	react {
        case message:Message => 
          trace2("#%s message received: %s", debugId, message.toJson)
          clientChanges(message)
        case (source:SyncNode, changes:Seq[_]) =>
          trace2("#%s changes received from %s:  %s", debugId, source, changes mkString("\n"))
          applyChanges(source, changes.asInstanceOf[Seq[DataChange]])
        case Flush() => // for tests, allows synchronous wait that prior messages have been processed
        	trace2("#%s flush", debugId)
          reply("flushed")
        case x =>
          abort2("unexpected message received %s", x.asInstanceOf[AnyRef])
      }
    }
  }
  
  private def clientChanges(message:Message) {
	  ProcessMessage.process(message, this)    
  }

  
  /** called when we receive a transactions worth of changes from the partition */
  private def applyChanges(source:SyncNode, changes:Seq[DataChange]) {
    withApp {
      trace2("#%s Changes received from %s %s", app.debugId, source, changes mkString("\n"))

      // preload all of the objects referenced by the changes.  
      // (e.g. a property change may reference an object we don't have yet)
      changes flatMap {_.references} foreach {get(_)}

      // apply the modifications 
      changes foreach {modify(_)}
      
      // don't send changes back to the source
      tossReflectedChanges(source)
      
    } // as withApp commits, client and partition notifications are sent (there may be partition changes subsequent to toss) 
  }
  
  /** don't send changes back to the partition or client that sent them here */
  private def tossReflectedChanges(source: SyncNode) {
    import StringUtil.indent
    import String.format
    
	  source match {
      case p:PartitionId =>
        // don't resend partition changes back to the partition
        val toss = instanceCache.drainChanges()         
        ifTrace2 {
          format("#%s partitionChanged() tossing %s partition changes: %s", debugId.toString,
        		toss.length.toString, StringUtil.indent(toss) )
        }
      case _ => NYI()      
    }
  }
  
  /** apply one modification */
  private def modify(change:DataChange) {
    remoteChange.withValue(change) {
      change match {
        case created:CreatedChange => NYI()
        case property:PropertyChange => 
          withGetId(property.target) {obj =>
            trace2("#%s applying property: %s", debugId, change)
            SyncableAccessor(obj).set(obj, property.property, getValue(property.newValue))          
          }
        case collectionChange:CollectionChange => 
          withGetId(collectionChange.target) {obj =>
            trace2("#%s collectionChange : %s", debugId, change)
            obj match {
              case collection:SyncableCollection => collection.revise(collectionChange)
            }
          }
        case deleted:DeletedChange => NYI()        
      }
    }
  }
  
  private def getValue(value:SyncableValue):AnyRef = 
    value.value match {
      case ref:SyncableReference => get(ref) get
      case v => v.asInstanceOf[AnyRef]
    }
  
}

// SOON, when we switch to 2.8 we can make this a reactor and use typed messages..
