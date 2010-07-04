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

import org.scala_libs.jpa.LocalEMF
import org.scala_libs.jpa.ScalaEntityManager
import net.lag.logging.Logger
import com.digiting.util.Configuration
import com.digiting.sync._
import scala.util.DynamicVariable
import UserEntityManagerFactory._


/** holds the currently active EntityManager */
object CurrentEntityManager {
  val entities = new DynamicVariable[Option[ScalaEntityManager]](None)
  
  /** run the function with a new JPA entity manager in context.  Close the entity manager
   *  when the function completes */
  def withNewEntityManager[T](fn: =>T):T = {
    var entities = Some(entityManagerFactory.newEM)
    try {      
      CurrentEntityManager.entities.withValue(entities) {
        fn
      }
    } finally {
      entities map {_.close() }
    }
  }
}

object UserEntityManagerFactory {
  val entityManagerFactory = new LocalEMF(Configuration("login-persistenceUnit")) 
}

/** when mixed into an AppContext, this trait instantiates a JPA entity manager for 
 * each incoming transaction */
trait HasUserEntityManager extends AppContext {
  /** provide a JPA entity manager for the function running in this application's context.
    * close the entity manager when the function completes. */
  override def withApp[T](fn: =>T):T = {
    CurrentEntityManager.withNewEntityManager {
      super.withApp(fn)
    }
  }  
}
