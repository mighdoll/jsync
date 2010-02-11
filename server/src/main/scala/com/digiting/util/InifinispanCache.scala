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
