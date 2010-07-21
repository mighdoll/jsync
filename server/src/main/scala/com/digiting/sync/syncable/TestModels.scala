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
  def this() = this(null)
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

class TestRefObj[T <: Syncable](var ref:T) extends Syncable {
  def this() = this(null.asInstanceOf[T])
  val kind = "$sync.test.refObj"
}

object TestRefObj {
  def apply[T <:Syncable](ref:T) = new TestRefObj[T](ref)
  def apply[T <:Syncable]() = new TestRefObj[T]()
}

class TestTwoRefsObj extends Syncable {
  val kind = "$sync.test.twoRefsObj"
  var ref1:Syncable = null;
  var ref2:Syncable = _
}

class TestParagraph(var text:String) extends Syncable {
  def this() = this(null)
  val kind = "$sync.test.paragraph"
}

class TestPrimitiveProperties extends Syncable {
  val kind = "$sync.test.primitiveProperties"
  var b:Byte = _
  var s:Short = _
  var i:Int = _
  var l:Long = _
  var t:Boolean = _
  var c:Char = _
  var f:Float = _
  var d:Double = _
}


class KindVersion extends Syncable {
  val kind = "$sync.test.kindVersioned"
  override def kindVersion = "1"
  var obj:TestRefObj[TestNameObj] = _
  
  def copyTo(otherVersion:KindVersion0) {
	  otherVersion.obj = obj.ref
  }
}

object KindVersion0 {
  def apply(named:TestNameObj) = {
    val o = new KindVersion0()
    o.obj = named
    o
  }
}

class KindVersion0 extends Syncable with MigrateTo[KindVersion] {
  val kind = "$sync.test.kindVersioned"
  var obj:TestNameObj = _
  
  def copyTo(otherVersion:KindVersion) {
    otherVersion.obj = TestRefObj(obj)
  }
}

class TwoBrowsers extends Syncable {
	val kind = "$sync.test.twoWindows"
  var obj:Syncable = _
  var cmd:String = _
}
