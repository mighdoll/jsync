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


object ClassReflection {
  import java.io.File
  import java.net.URI
  val log = Logger("ClassReflection")
  
  def collectClasses[T](seedClass:Class[_], superClass:Class[T]):Iterable[Class[T]] = {
    val classMatch = """(.*).class""".r
    val seedLocation = 
      seedClass.getProtectionDomain().getCodeSource().getLocation()
    
    val classParts = seedClass.getName split ('.') 
    val packageParts = classParts.take(classParts.length - 1)
    val packageName = packageParts mkString "."
    val loader = seedClass.getClassLoader
    val syncableClass = classOf[Syncable]
    
    import File.separator
    val packagePath = packageParts.mkString(separator)
    val fullLocation = seedLocation.toString + packagePath
    val dir = new File(new URI(fullLocation))
    
    val found = 
      for {
        file <- dir.listFiles
        className :: Nil <- classMatch.unapplySeq(file.getName)
        if !className.contains("$")
        foundClass = loader.loadClass(packageName + "." + className)
        a = {log.trace("collectClasses(), class: %s %s", foundClass.getName, 
                      superClass.isAssignableFrom(foundClass)); 0}
        if superClass.isAssignableFrom(foundClass)
      } yield
        foundClass.asInstanceOf[Class[T]]

    found
  }

}
