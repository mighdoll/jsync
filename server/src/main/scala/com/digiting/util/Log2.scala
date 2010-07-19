package com.digiting.util
import net.lag.logging.Logger

object Log2 {
  def logger(loggerName:String) = Logger(loggerName)  

  /** Level 400 logging wrapper.  Returns None so it can be used in for comprehensions */ 
  def trace2[T](message:String, params:Any*)(implicit log:Logger):Option[T] = {
    log.trace(message, params:_*)
    None
  }
  def ifTrace2[T](fn: =>Object)(implicit log:Logger):Option[T] = {
    log.ifTrace(fn)
    None
  }
  /** Level 500 logging wrapper.  Returns None so it can be used in for comprehensions */ 
  def debug2[T](message:String, params:Any*)(implicit log:Logger):Option[T] = {
    log.debug(message, params:_*)
    None
  }
  /** Level 800 wrapper.  Returns None so it can be used in for comprehensions */ 
  def info2[T](message:String, params:Any*)(implicit log:Logger):Option[T] = {
    log.info(message, params:_*)
    None
  }
  /** Level 900 logging wrapper.  Returns None so it can be used in for comprehensions */ 
  def warn2[T](message:String, params:Any*)(implicit log:Logger):Option[T] = {
    log.warning(message, params:_*)
    None
  }
  /** Level 900 logging wrapper.  Returns None so it can be used in for comprehensions */ 
  def warning2[T](message:String, params:Any*)(implicit log:Logger):Option[T] = {
    log.warning(message, params:_*)
    None
  }  
  /** Level 930 logging wrapper.  Returns None so it can be used in for comprehensions */ 
  def err2[T](message:String, params:Any*)(implicit log:Logger):Option[T] = {
    log.error(message, params:_*)
    None
  }
  /** Level 930 logging wrapper.  Returns None so it can be used in for comprehensions */ 
  def error2[T](message:String, params:Any*)(implicit log:Logger):Option[T] = {
    log.error(message, params:_*)
    None
  }
  /** Level 970 logging wrapper.  Returns None so it can be used in for comprehensions */ 
  def critical2[T](message:String, params:Any*)(implicit log:Logger):Option[T] = {
    log.critical(message, params:_*)
    None
  }
  /** Level 1000 logging wrapper.  Returns None so it can be used in for comprehensions */ 
  def fatal2[T](message:String, params:Any*)(implicit log:Logger):Option[T] = {
    log.fatal(message, params:_*)
    None
  }


  /** log an error and return a Left */
  def errLeft2[T](message:String, params:AnyRef*)(implicit log:Logger):Left[String, T] = {
    val errorMessage = String.format(message, params:_*)
    log.error(errorMessage)
    Left(errorMessage)
  }
  
  /** log an error and throw an exception */
  def abort2[T](message:String, params:AnyRef*)(implicit log:Logger):T = {
    val errorMessage = String.format(message, params:_*)
    fatal2(errorMessage)
    throw new ImplementationError(errorMessage)
  }
    
}
