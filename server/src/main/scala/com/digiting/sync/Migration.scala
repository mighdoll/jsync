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
import net.lag.logging.Logger

/** a syncable that is migrated as it's read */
trait Migration[T <: Syncable] extends Syncable {
  private val _log = Logger("Migration")
  /** copy data from the migrating instance to the new version */
  def copyTo(target:T)
 
  /** migrate this old instance to a current version one with the same id */
  def migrate:Syncable = {
    val migrated = Observers.withNoNotice {
      newSyncable(kind, SyncableIdentity(id, partition))
    } getOrElse {
      throw new ImplementationError("migrate() can't create syncable for kind: " + kind)
    }
    SyncManager.currentPartition.withValue(partition) { // new objects in copy should go in same partition
      copyTo(migrated.asInstanceOf[T])
    }
    _log.info("migrated %s kindVersion: %s  to  %s kindVersion: %s", this, 
              this.kindVersion, migrated, migrated.kindVersion)
    migrated
  }
  
}

// CONSIDER renaming to MigrationTo?
