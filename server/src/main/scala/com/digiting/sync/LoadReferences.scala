/*   Copyright [2010] Digiting Inc
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
import com.digiting.util._

/** utiliy routines to bulk load SyncableReferences, useful for loading the members of a collection */
object LoadReferences extends LogHelper {
  val log = Logger("LoadRefs")  
  
    /** SOON parallel or batch load multiple objects from the backend for speedier loading from e.g. simpledb */
  def loadRefs(collection:SyncableCollection, 
      refs:Iterable[SyncableReference]):Iterable[Syncable] = {    
    for {
      ref <- refs
      syncable <- App.app.get(ref.id) orElse
        err("loadRefs can't find target: %s in collection %s", ref, collection.fullId)
    } yield 
      syncable
  }
  
  def loadMapRefs[T](collection:SyncableCollection, 
      refs:Map[T,SyncableReference]):Iterable[(T,Syncable)] = {
    for {
      (key, ref) <- refs
      syncable <- App.app.get(ref) orElse 
        err("loadMapRefs can't find target: %s in collection %s", ref, collection.fullId)
    } yield {
      (key, syncable)
    }
  }        
}
