package com.digiting.util
import org.infinispan._
import org.infinispan.manager._
import collection.mutable

/** an infinispan cache wrapped in a scala style map interface */
class InifinispanCache[K,V] extends mutable.Map[K,V] {
  private val manager = new DefaultCacheManager();
  private val cache:AdvancedCache[K,V] = manager.getCache[K, V].getAdvancedCache
    
  def get(key:K):Option[V] = {
    cache.get(key) match {
      case null => None
      case v => Some(v)
    }
  }
  
  def -=(key:K) {
    cache.remove(key)
  }
  def update(key:K, value:V) {
    cache.put(key,value)
  }
  
  def size():Int = {
    cache.size
  }
  
  def elements:Iterator[(K,V)] = {
    null
  }
  
}
