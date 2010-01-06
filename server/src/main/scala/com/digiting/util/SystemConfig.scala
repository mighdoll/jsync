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
package com.digiting.util
import TryCast.nullableStringToOption

object SystemConfig {  
  def getenv(key:String) = {
    nullableStringToOption(System.getenv(key))
  }
  
  def getProperty(key:String) = {
    nullableStringToOption(System.getProperty(key))
  }
  
  
  /** get a value from java system properties or the operating system
    * environment varaibles.   */
  def getPropertyOrEnv(key:String):Option[String] = {
    getProperty(key) orElse 
      getenv(key)
  }

}

