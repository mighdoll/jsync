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

/** Subscription root object shared between the client and the server.
 * It's created by the client with a name, and then the server
 * fills in the root object.   (The changed root is then naturally 
 * propogated back to the client) */
class Subscription extends Syncable {
  val kind = "$sync.subscription"
  var name:String = _
  var inPartition:String = _
  var root:Syncable = _
}

class SyncableJson extends Syncable with LocalOnly {
  var kind = "syncableJson-overrideMe"	
}

class SyncString(var string:String) extends Syncable {
  def this() = this("")
  val kind = "$sync.syncString"
}

class ServiceCall[T <: Syncable] extends Syncable {
  val kind = "$sync.serviceCall"
  var parameters:SyncableSeq[T] = _   
  var results:Syncable = _
  
  def arguments(method:java.lang.reflect.Method):Seq[AnyRef] = {
    parameters.map { parameterFromMessage(_) }    
  }
  
  private def parameterFromMessage(param:Syncable):AnyRef = {
    param match {
      case s:SyncString =>
        s.string
      case _ =>
        param;
    }
  }
}

/** SOON these are for the demo site and belong in a separate package */
class Settings extends Syncable {
  def kind = "com.liquidj.site.settings"
  var user:User = _
  var reminders:SyncableSeq[Reminder] = _
  var currentDemo:String = ""
}

class Reminder extends Syncable {
  def kind = "com.liquidj.site.Reminder"
  var note = ""
  var done = false
}


class User extends Syncable {
  def kind = "com.liquidj.site.user"
  var firstName = ""
  var lastName = ""
  var email = ""
}
