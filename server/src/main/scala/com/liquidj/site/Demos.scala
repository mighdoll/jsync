/*   Copyright [2010] Lee Mighdoll
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
package com.liquidj.site
import com.digiting.sync.Applications
import com.digiting.sync.Connection
import com.digiting.sync.RichAppContext
import com.digiting.sync.Syncable
import com.digiting.sync.RamPartition
import com.digiting.sync.SyncManager

  
object Demos {
  def init() {}
  
  Applications.register {
    case("demo" :: "sync" :: Nil, message, connection) =>
      new DemoContext(connection)
  }
  SyncManager.registerKindsInPackage(classOf[DemoData])
}

class DemoContext(connection:Connection) extends RichAppContext(connection) {
  val appName = "Demos"
  
  val demos = new RamPartition("demos")
  demos.publish("settings", 
    SyncManager.withPartition(demos) {
      val settings = new DemoData
      settings.fieldDemo = new FieldDemo
      settings
    }
  )
}

class DemoData extends Syncable {
  def kind = "com.liquidj.site.DemoData"
  var fieldDemo:FieldDemo = _
}

class FieldDemo extends Syncable {
  def kind = "com.liquidj.site.FieldDemo"
  var number:Int = _
}
