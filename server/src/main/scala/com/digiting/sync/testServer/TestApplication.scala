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
package com.digiting.sync.testServer
import net.lag.logging.Logger
import collection.mutable.ListBuffer

object TestApplication {
  private val tests = new ListBuffer[AnyRef]()
  private var registered = false
  val appVersion = "0.1"
  
  init()
  
  /** initialize tests.  Idempotent - call early and often.  */
  def init() {
    if (!registered) {
      Applications.register {
        case ("test" :: "sync" :: Nil, message, connection) => 
          val app = new TestContext(connection)
          tests foreach { app.createImplicitServices(_) }
          app
      }
      TestSubscriptions.init()
      registered = true
    }
  }
  
  /** register implicit services with the test application */
  def registerTestServices(serviceObject:AnyRef) {
    tests += serviceObject    
  }
  
  
}

class TestContext(connection:Connection) extends RichAppContext(connection) {
  override val appVersion = TestApplication.appVersion
  override val appName = "test-application-context"
}
  

