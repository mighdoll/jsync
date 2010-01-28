package com.digiting.sync
import net.lag.logging.Logger

/** Holds a value that's validated as legal for synchronization.
 * 
 * Currently, primitive numeric types, strings,
 * and references to other syncable objects may be synchronized.
 */
class SyncableValue {
  private[this] var validated:Any = () => throw new ImplementationError  // should be overriden by constructor
  
  def value() = validated
  
  def this(value:Null) = { this(); validated = value}
  def this(value:SyncableId) = { this(); validated = value}
  def this(value:String) = { this(); validated = value}
  def this(value:Boolean) = { this(); validated = value}
  def this(value:Char) = { this(); validated = value}
  def this(value:Short) = { this(); validated = value}
  def this(value:Int) = { this(); validated = value}
  def this(value:Long) = { this(); validated = value}
  def this(value:Double) = { this(); validated = value}
    
  override def toString = {
    validated match {
      case null => "null"
      case x => x.toString
    }
  }
}

object SyncableValue {
  val log = Logger("SyncableValue")
  def apply(value:SyncableId) = new SyncableValue(value)
  def apply(value:String) = new SyncableValue(value)
  def apply(value:Boolean) = new SyncableValue(value)
  def apply(value:Char) = new SyncableValue(value)
  def apply(value:Short) = new SyncableValue(value)
  def apply(value:Int) = new SyncableValue(value)
  def apply(value:Long) = new SyncableValue(value)
  def apply(value:Double) = new SyncableValue(value)
  def apply(value:Null) = new SyncableValue(value)
  
  def convert(value:Any) = {
    value match {
      case s:Syncable => new SyncableValue(s.fullId)
      case v:SyncableId => new SyncableValue(v)
      case v:String => new SyncableValue(v)
      case v:Boolean => new SyncableValue(v)
      case v:Char => new SyncableValue(v)
      case v:Short => new SyncableValue(v)
      case v:Int => new SyncableValue(v)
      case v:Long => new SyncableValue(v)
      case v:Double => new SyncableValue(v)
      case null => new SyncableValue(null)
      case r:AnyRef => 
        log.error("unexpected type: %s of type: %s", r, r.getClass)
        throw new IllegalArgumentException        
      case x => 
        log.error("unexpected argument: %s ", x)
        throw new IllegalArgumentException
    }
  }
}


