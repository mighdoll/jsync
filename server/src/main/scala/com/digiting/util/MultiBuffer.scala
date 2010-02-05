package com.digiting.util
import collection.mutable.{Buffer, ListBuffer, HashMap}


trait MultiBuffer[K, V] extends HashMap[K, Buffer[V]] {
  def insert(key:K, value:V, at:Int) {
    buffer(key) insert(at, value)
  }
  
  def append(key:K, value:V) {
    buffer(key) append(value)
  }
  
  def remove(key:K, at:Int):V = {
    buffer(key) remove(at)
  }
  
  private[this] def buffer(key:K):Buffer[V] = {
    get(key) match {
      case Some(found) => found
      case None =>
        val newBuf= new ListBuffer[V]
        this(key) = newBuf
        newBuf
    }    
  }
  
}
