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
package com.digiting.sync.test
import org.scalatest.SuperSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AllSyncTests extends SuperSuite (
  List(
    new ConfigurationTest,
    new ObservationTest,
    new SyncableTest,
    new SyncableAccessorTest, 
    new MultiMapTest,
    new ParseTest,
    new MigrationTest,
    new ProtocolTest,
    new ProtocolReferenceTest,       
    new InstanceVersionTest,
    new PickleTest,
    new RamPartitionTest,
    new RamWatchesTest,
    new AppWatchTest
    )
  ) 

