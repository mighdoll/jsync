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

import collection._
import com.digiting.sync.aspects.Observable
import com.digiting.sync.syncable._


object SyncManager {
    type Kind = String
  
    val localObjects = mutable.Map.empty[String, Syncable]   // LATER this should be a WeakMap
    val metaAccessors = mutable.Map.empty[Kind, ClassAccessor]
    
    registerSyncableKinds()
    setupTestSubscriptions()
    
    def getOrMake(id:String, kind:Kind):Syncable = {
        localObjects get id
        match {
            case Some(obj) => obj
            case _ => createObj(id, kind)
        }
    }

    def put(id:String, obj:Syncable) =
        localObjects(id) = obj

    def createObj(newId:String, kind:Kind):Syncable = {
        val obj = metaAccessors.get(kind) match {
          case Some(meta) => constructSyncable(meta.theClass.asInstanceOf[Class[Syncable]], newId)
          case _ => {
            val obj = new SyncableMap()
            obj.setId(newId)
            obj } }

//        Console println "created obj: " + obj
        put(newId, obj)
        obj 
    }
    
    def constructSyncable(clazz:Class[Syncable], newId:String):Syncable = {
      val obj = clazz.newInstance
      obj.setId(newId)
      obj
    }
    
    def registerKind(kind:Kind, clazz:Class[T] forSome {type T <: Syncable}) = {
      metaAccessors + (kind -> SyncableAccessor.get(clazz))      
    }
    
    def registerSyncableKinds() {
      /* manually for now, later search packages for all Syncable classes, and register them (use aspectJ search?) */
      SyncManager.registerKind("$sync.subscription", classOf[SubscriptionRoot])      
      SyncManager.registerKind("$sync.set", classOf[SyncableSet[_]])      
    } 
    
    def copyFields(srcFields:Iterable[(String, AnyRef)], target:Syncable) = {
      for ((propName, value) <- srcFields) {
        metaAccessors(target.kind).set(target, propName, value)
      }
    }    
    
    def setupTestSubscriptions() {
      val nameObj = new TestNameObj
      nameObj.setId("#testName1")
      nameObj.name = "emmett"
      Subscriptions.create("$sync/test/oneName", nameObj)
    }
    
    def commit() = {
      for (watcher <- commitWatchers) {
        watcher()
      }
    }
    
    val commitWatchers = mutable.Set[()=>Unit]()
    
    def watchCommit(func:()=>Unit) {
      commitWatchers + func
    }
    
    var nextId:Int = 0
    def newSyncableId = {
      val id = "server-" + nextId
      nextId += 1
      id
    }
}


