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
import com.digiting.sync._
//
//object AdminSetup {
//  val log = Logger("AdminSetup")
//  def init() {
//    log.trace("init")
//    Applications.register {
//      case ("admin" :: "sync" :: Nil, message, connection) => new AdminContext(connection)
//    }    
//  }
//}
//
//class AdminContext(connection:Connection) extends AppContext(connection:Connection) with HasUserEntityManager {
//  val log = Logger("AdminContext")
//  
//  createImplicitServices(Admin2)
//}
//
//@ImplicitServiceClass("Admin2")
//object Admin2 {
//  val log = Logger("Admin2")
//  val domainName = UserPartitions.userPartitionsDomain
//  val admin = UserPartitions.simpleDbAdmin
//  val domain = admin.domains(domainName)
//  val baseQuery:String = String.format("itemName() from %s where kind is not null ", domainName)      
//  
//  @ImplicitService
//  def simpleDbQuery(query:String):Syncable = {
//    if (query.trim != "") {
//      val fullQuery = baseQuery + " and " + query
//      log.trace("simpleDbQuery, fullQuery: " + fullQuery)
//      
//      var results = 
//        for {
//          itemName <- UserPartitions.simpleDbAdmin.account.select(fullQuery)
//          syncableId <- SyncableId.unapply(itemName.name)
//          partition <- UserPartitions.ensureUserPartitionExists(syncableId.partitionId)
//          a = log.trace("identified partition %s", partition)
//          syncable <- SyncManager.get(syncableId.partitionId, syncableId.instanceId)
//        } yield {
//          log.trace("retrieved syncable %s", syncable)
//          syncable
//        }
//      
//      App.withTransientPartition {
//        val set = new SyncableSet[Syncable]
//        set ++= results
//        set
//      }      
//    } else {
//      log.warning("simpleDbQuery: ignoring empty query: %s", query)
//      null
//    }    
//  }
//  
//  
//}
//

