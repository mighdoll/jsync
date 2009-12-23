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
import net.lag.configgy._
import net.lag.logging.Logger
import scala.util.matching.Regex
import scala.collection._
import com.digiting.sync.ImplementationError


/**
   * Access to a set of configuration data, typically stored in configgy .conf files.
   * 
   * Uses a 'runMode' setting to select between configuration settings.
   * 
   * Selectively exports configuration settings to log4j (and eventually more, 
   *  e.g. hibernate).
   * 
   * Allows overriding configuration for tests.
   * 
   * Allows setting configuration variables from java system properties, or from
   * the OS environment.
   * 
   * Searches the following configuration maps in order:  
   *   withOverride (testing), 
   *   configgy (.conf files), runmode section
   *   configgy (.conf files), non runmode section
   *   system properties (-Dproperty=value), 
   *   OS environment variables ($property=value)
   *
   * So the first few key checks are:
   *   runMode.key from withOverride
   *   key from withOverride
   *   runMode.key from .conf (configgy)
   *   key from .conf
   *   ...
   * 
   * 
   * LATER consider using separate .conf files for each runMode, rather than
   *   using sections in the same file
   * LATER consider merging all attributes into one map, it would simplify the code.
*/
import net.lag.configgy.ConfigException
object Configuration {   
  var propOverrides:Map[String,Option[String]] = new immutable.HashMap[String,Option[String]]
  lazy val log = Logger("Configuration")
  var initialized = false
  var runMode:String = null
  var runModeMap:ConfigMap = null
    

  private def error[T](message:String, params:String*):Option[T] = {
    log.error(message, params:_*)
    None
  }

  def reset() {
    Configgy.configureFromResource("context.conf", this.getClass.getClassLoader)
    runMode = determineRunMode
    runModeMap = findRunModeMap(runMode)

    setupConfiggyLogging()
    log.info("runMode: %s", runMode)
    processSettings()
  }  
  
  private def findRunModeMap(mode:String):ConfigMap = {
    Configgy.config getConfigMap(mode) getOrElse {
      throw new ConfigException("can't find runMode: " + mode)}
  }
      
  def init() {
    if (!initialized)
      reset();
    initialized = true
  }  

  /** apply our special sauce to each configuration element */
  private def processSettings() {
    for {
      (key, value) <- runModeMap.copy.asMap
    } {
      keyChanged(key, value)
    }
  }
  
  private def setupConfiggyLogging() {
    runModeMap.getConfigMap("log") match {
      case Some(logMap) => 
        Logger.configure(logMap, false, false)
      case _ =>
        throw new ConfigException("can't find log section")
    }
  }
  
  /** drive special handling of each configuration value */
  def keyChanged(key:String, value:String) {
    log.trace("keyChanged: %s = %s", key, value)
    val keyParts = List.fromString(key, '-')
    keyParts match {
      case "logoverride" :: "log4j" :: "logger" :: tail  => 
        log4jLogEntry(value)
      case prefix :: "logoverride" :: tail => 
        logOverrideEntry(value)
      case "logoverride" :: tail => 
        logOverrideEntry(value)
      case _ =>
    }
  }

  import net.lag.logging.Level
  
  /** handy extractor for log override values */
  object LogOverride {
    val logOverrideMatch = """(.*)\s*=\s*(.*)\s*""".r
    def unapply(entry:String):Option[(String,String)] = {
      for {
        packageName :: levelName :: Nil <- logOverrideMatch.unapplySeq(entry)
      } yield
        (packageName, levelName)
    }
  }
  
  /** handle one Configgy log entry in the config file */
  private def logOverrideEntry(entry:String) {
    for {
      (packageName, levelName) <- LogOverride.unapply(entry)
    } {
      setLogLevel(packageName, Some(levelName))
    }
  }
  
  /** handle one log4j logger entry in the config file */
  private def log4jLogEntry(entry:String) = {
    for {
      (packageName, levelName) <- LogOverride.unapply(entry)
    } {
      setLog4JLevel(packageName, Some(levelName))
    }
  }
  
  
  /** set the level for one configgy logger */
  private def setLogLevel(packageName:String, levelNameOpt:Option[String]) {
    val levelName = levelNameOpt getOrElse 
      { throw new ConfigException("level can't be unset in Configgy: " + packageName) }
    for {
      level <- Logger.levelNames get levelName.toUpperCase orElse 
        error("log level %s for %s not recognized", levelName, packageName)
     } {
      log.info("log %s to %s", packageName, level)
      Logger(packageName).setLevel(level)
     }
  }
  

  /** set the level for one log4j logger */
  private def setLog4JLevel(packageName:String, levelNameOpt:Option[String]) {
    val level = levelNameOpt getOrElse null
    log.info("log4j %s to %s", packageName, level)
    
    var log4Logger = org.apache.log4j.Logger.getLogger(packageName)
    log4Logger.setLevel(org.apache.log4j.Level.toLevel(level))    
  }
    

  /** Get a configuration value.  
   * 
   * The key can be in a runmode section or in the base section
   */
  def getString(key:String):Option[String] = {    
    propOverrides get key getOrElse {  
      runModeMap.getString(key) 
    } 
  }
  
  /** get a configuration value or throw an exception */
  def apply(key:String):String = {
	getString(key) match {
	  case Some(string) => string
      case None => throw new ConfigException("key: " + key + " not found")
    }
  }

  /** property or environment variable name that sets the runmode for this application */
  private def runModeKey:Option[String] = {
    Configgy.config.getString("Configuration.name") map {appName =>
  	  appName + "_" + "runMode"
    }
  }
  
  /** get the runmode for this app, or "debug" by default */
  private def determineRunMode:String = {
    val modeOpt:Option[String] =
      for {
        modeKey <- runModeKey
        modeFound <- propOverrides get modeKey getOrElse
          SystemConfig.getPropertyOrEnv(modeKey) 
      } yield 
        modeFound

    modeOpt getOrElse
      "debug" 
  }
  
  /** temporarily override a property value, useful for testing.  Passing None will
    * force the property to be unset */
  def withOverride(overrides:(String, Option[String])*)(body: =>Unit) {
    val overridesMap = immutable.HashMap[String, Option[String]](overrides:_*)
    val orig = new mutable.HashMap[String,Option[String]]

    // run it through our configuration observer if it's a value
    for {
      (key,valueOpt) <- overridesMap
      value <- valueOpt
    } {
      val normalizedKey = key.toLowerCase
      log.trace("withOverride, setting %s = %s, was: %s", normalizedKey, value, getString(normalizedKey))
      orig += (normalizedKey -> getString(normalizedKey))
      keyChanged(normalizedKey, value)      // grr, configgy forces keys to be lower case
    }
    
    
    val origOverrides = propOverrides
    propOverrides = overridesMap ++ origOverrides
    body
    propOverrides = origOverrides
    
    // run our config observer on the original values
    for {(key, valueOpt) <- orig} {
      log.trace("withOverride, resetting %s = %s", key, valueOpt)
      valueOpt match {
        case Some(value) => keyChanged(key, value)            
        case _ => 
          log.warning("Unsetting value of overriden key: %s is NYI.  Key had no original value.  ", key)
      }
    }
  } 
  
}


object SystemConfig {  
  def getenv(key:String) = {
    toOption(System.getenv(key))
  }
  
  def getProperty(key:String) = {
    toOption(System.getProperty(key))
  }
  
  def toOption(value:String):Option[String] = {
    if (value != null) {
      Some(value)
    } else {
      None
    }    
  }
  
  /** get a value from java system properties or the operating system
    * environment varaibles.   */
  def getPropertyOrEnv(key:String):Option[String] = {
    getProperty(key) orElse 
      getenv(key)
  }

}

