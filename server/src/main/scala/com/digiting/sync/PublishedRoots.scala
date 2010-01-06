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
package com.digiting.sync

import net.lag.logging.Logger
import collection.mutable.HashMap


/** An avalailable subscription within a partition.  
 *
 * Internally, each root is stored within the partition with a unique id: #published-name 
 */
class PublishedRoot(var name:String, 
                    var root:Syncable) extends Syncable {
  def this() = this("", null)
  def kind = "$sync.server.publishedRoot"		// not currently sent to client  
}

/** 
 * Manages the set of well known subscriptions within a partition.  Subscriptions
 * are mappings from a string key to syncable objects.
 * 
 * Normal subscriptions are persisted in the partition itself, and are permanent
 * with the partition.  Generated subscriptions call a function when the key is requested,
 * and are not persistent between server restarts.
 */
class PublishedRoots(partition:Partition) { 
  val log = Logger("PublishedRoots")
  val generatedRoots = new HashMap[String, ()=>Option[Syncable]]
  
  /** create a new peristent mapping from a name to root object to which
   * clients can subscribe.  Any existing subscription at the same name will be
   * replaced */
  def create(name:String, root:Syncable) {    
    val ids = SyncableIdentity(nameToId(name), partition)
    val published = 
      SyncManager.setNextId.withValue(ids) {
	    SyncManager.currentPartition.withValue(partition) {
	      new PublishedRoot(normalize(name), root)
	    }
      }
    
    log 
    partition.put(published)
  }
    
  /** create a dynamic subscription, that calls a function to produce */
  def createGenerated(name:String, fn:()=>Option[Syncable]):Unit = {
    find(name) match {
      case Some(data) =>
        log.error("creating generated subscription function atop data! " + name)
      case _ =>
    }
    
    generatedRoots += (name -> fn)    
  }
    
  /** find an advertised subscription */
  def find(name:String):Option[Syncable] = {    
    generatedRoots get name match {
      case Some(fn) => fn()
      case None => findPersistent(name)
    }
  }
  
  /** find an advertised subscription in the persistent data pool */
  private def findPersistent(name:String):Option[Syncable] = {
    val normalName = normalize(name)
    partition get nameToId(name) match { 
      case Some(root:PublishedRoot) => 
        assert (normalize(name) == root.name)
        log.trace("findPersistent() found: %s", name)
        Some(root.root)
      case Some(x) => 
        log.error("PublishedRoots.find() found unexpected object type: " + x)
        throw new ImplementationError
      case None => None
    }
  }
  
  /** convert a name */
  private def nameToId(name:String):String = {
    "#published-" + normalize(name)    
  }
  
  /** convert subscription name to standard form by removing leading slashes, trimming spaces, etc.*/
  private def normalize(name:String):String = {
    val str = 
      if (name.startsWith("/")) 
	      name.substring(1,name.length)
	    else
	      name
    str.trim
  }
  
}