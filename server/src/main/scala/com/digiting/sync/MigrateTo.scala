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
import SyncManager.newSyncable
import com.digiting.util._
import Log2._

/** a syncable of an old kind version that is migrated to a new class as it's read */
trait MigrateTo[T <: Syncable] extends Syncable {
  implicit private val log = logger("MigrateTo")
  /** copy data from the migrating instance to the new version */
  def copyTo(otherVersion:T)
 
  /** migrate this old instance to a current version one with the same id and version */
  def migrate:Syncable = {
    val migrated:Syncable = Observers.withNoNotice {
      newSyncable[Syncable](kind, id)
    } 
    SyncManager.withPartition(partition) { // new objects in copy should go in same partition
      copyTo(migrated.asInstanceOf[T])
    }
    info2("migrated %s kindVersion: %s  to  %s kindVersion: %s", this, 
              this.kindVersion, migrated, migrated.kindVersion)
    migrated
  }  
}



