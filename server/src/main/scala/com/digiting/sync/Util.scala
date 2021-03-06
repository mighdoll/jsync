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
import com.digiting.util._

class ProtocolException(message:String) extends Exception(message) {
  def this() = this("")
}


object StringUtil {
  def firstLine(s:String) = {
    s.lines.take(1).mkString    
  }
  def indent(iter:Iterable[AnyRef]):String = { 
	  val strings = iter map {_.toString}
    strings.foldLeft[String](""){(a,b) => a + "\n   " + b}       
  } 

}
