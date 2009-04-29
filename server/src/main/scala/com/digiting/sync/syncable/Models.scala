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
package com.digiting.sync.syncable

import com.digiting.sync.Syncable

/* Subscription root object shared between the client and the server.
 * It's created by the client with a name, and then the server
 * fills in the root object.   (The changed root is then naturally 
 * propogated back to the client) */
class SubscriptionRoot extends Syncable {
  val kind = "$sync.subscription"
  var name:String = _
  var root:Syncable = _
}
