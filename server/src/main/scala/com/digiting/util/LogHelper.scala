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
import net.lag.logging.Logger

trait LogHelper {
  protected val log:Logger
  
  /** Level 400 logging wrapper.  Returns None so it can be used in for comprehensions */ 
  def trace[T](message:String, params:Any*):Option[T] = {
    log.trace(message, params:_*)
    None
  }
  /** Level 500 logging wrapper.  Returns None so it can be used in for comprehensions */ 
  def debug[T](message:String, params:Any*):Option[T] = {
    log.debug(message, params:_*)
    None
  }
  /** Level 800 wrapper.  Returns None so it can be used in for comprehensions */ 
  def info[T](message:String, params:Any*):Option[T] = {
    log.info(message, params:_*)
    None
  }  
  /** Level 900 logging wrapper.  Returns None so it can be used in for comprehensions */ 
  def warn[T](message:String, params:Any*):Option[T] = {
    log.warning(message, params:_*)
    None
  }
  /** Level 900 logging wrapper.  Returns None so it can be used in for comprehensions */ 
  def warning[T](message:String, params:Any*):Option[T] = {
    log.warning(message, params:_*)
    None
  }  
  /** Level 930 logging wrapper.  Returns None so it can be used in for comprehensions */ 
  def err[T](message:String, params:Any*):Option[T] = {
    log.error(message, params:_*)
    None
  }
  /** Level 970 logging wrapper.  Returns None so it can be used in for comprehensions */ 
  def critical[T](message:String, params:Any*):Option[T] = {
    log.critical(message, params:_*)
    None
  }
  /** Level 1000 logging wrapper.  Returns None so it can be used in for comprehensions */ 
  def fatal[T](message:String, params:Any*):Option[T] = {
    log.fatal(message, params:_*)
    None
  }


  /** log an error and return a Left */
  def errLeft[T](message:String, params:AnyRef*):Left[String, T] = {
    val errorMessage = String.format(message, params:_*)
    log.error(errorMessage)
    Left(errorMessage)
  }
  
  /** log an error and throw an exception */
  def abort(message:String, params:AnyRef*) = {
    val errorMessage = String.format(message, params:_*)
    fatal(errorMessage)
    throw new ImplementationError(errorMessage)
  }
  
  /** Get a Configgy logger. */
  def logger(loggerName:String) = Logger(loggerName)  
}

