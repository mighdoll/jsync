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

class TestNameObj(var name:String) extends Syncable {
  def this() = this("")
  val kind = "$sync.test.nameObj"
}

object TestNameObj {
  def apply(name:String) = new TestNameObj(name)
  def apply() = new TestNameObj()
}

class TestValueObj extends Syncable {
  val kind = "$sync.test.valueObj"
  var value:String = _
}

class TestRefObj extends Syncable {
  val kind = "$sync.test.refObj"
  var ref:Syncable = null
}

class TestTwoRefsObj extends Syncable {
  val kind = "$sync.test.twoRefsObj"
  var ref1:Syncable = null;
  var ref2:Syncable = _
}

class TestParagraph(var text:String) extends Syncable {
  def this() = this("")
  val kind = "$sync.test.paragraph"
}

class TestPrimitiveProperties extends Syncable {
  val kind = "$sync.test.primitiveProperties"
  var b:Byte = _
  var s:Short = _
  var i:Int = _
  var l:Long = _
  var c:Char = _
  var t:Boolean = _
  var f:Float = _
  var d:Double = _
}


class KindVersion extends Syncable {
  val kind = "$sync.test.kindVersioned"
  override def kindVersion = "1"
  var ref:TestRefObj = _
}

class KindVersion0 extends Syncable with Migration[KindVersion] {
  val kind = "$sync.test.kindVersioned"
  var ref:TestNameObj = _
  def copyTo(newVersion:KindVersion) {
    newVersion.ref = new TestRefObj
    newVersion.ref.ref = ref
  }
}
