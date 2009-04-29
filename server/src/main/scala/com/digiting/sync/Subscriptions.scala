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
import Observation._



object Subscriptions {
  val db:mutable.Map[String, Syncable] = mutable.Map()
  val active:mutable.Map[String, Syncable] = mutable.Map()
  
  /* create a new peristent mapping from name to root object to which
   * clients can subscribe */
  def create(name:String, root:Syncable) = {
    // TODO store in real database
    db + (name -> root)
  }
  
  /* get a subscription from the database */
  private def fetch(name:String):Option[Syncable] = {
    db get name
  }
  
  /* subscribe */
  def subscribe(name:String):Option[Syncable] = {
    val root = active get name orElse fetch(name)     
    root
  }  
}
