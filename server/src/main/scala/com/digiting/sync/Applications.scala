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
import com.digiting.util.LogHelper
import net.lag.logging.Level._
import scala.collection.mutable

object Applications extends LogHelper {
  val log = Logger("Applications")

  private def error[T](message:String, params:String*):Option[T] = {
    log.error(message, params:_*)
    None
  }

  /** deliver a message to the appropriate registered application (asynchronously). 
   * returns the application that is processing the message */
  def deliver(syncPath:List[String], messageBody:String):Option[AppContext] = {
    val apps = 
      for {
        message <- ParseMessage.parse(messageBody) 
        app <- getAppFor(syncPath, message) orElse
          err("app not found for: %s", message.toString)
      } yield {
        log.ifTrace("delivering to app" + app.connection.debugId + "  messsage to: " +
           syncPath.mkString("/") +  "  message empty:" + message.isEmpty + ": " + messageBody)
        if (!message.isEmpty) 	  
          app receiveMessage(message)
        app
      } 
    
    apps find {_ => true}   // return Some(app) for the first app found, if any    
  }
  
  
  type Path = List[String]
  type AppContextCreator = PartialFunction[(Path, Message, Connection), AppContext]
  val appContextCreators = new mutable.ListBuffer[AppContextCreator]
  
  def register(creator:AppContextCreator) {
    appContextCreators += creator
  }
  
  def registerFirst(creator:AppContextCreator) {
    appContextCreators.insert(0, creator)
  }
  
  private def createAppContext(syncPath:List[String], message:Message, connection:Connection):AppContext = {
    appContextCreators.find(creator =>
      creator.isDefinedAt(syncPath, message,connection)
    ) match {
      case Some(creator) =>
        creator(syncPath, message, connection)
      case None =>
        log.warning("using default context, because no application context is defined for: message" + message.toJson)
        new AppContext(connection)
    }
      // CONSIDER: probably should allow registered apps to return true to isDefinedAt and a None response, meaning
      // that they might match, but didn't in this case.  
  }
  
  
  object HasToken {
    def unapply(message:Message):Option[String] = 
      message.findControlProperty("#token") match {
        case Some(token:String) => Some(token)
        case _ => None
      }      
  }

  
  object HasStart {
    def unapply(message:Message):Option[Any] = 
      message.findControlProperty("#start")  
  }
  
    
  /** find an app context for the incoming message, creating a new app context if necessary */
  private def getAppFor(syncPath:List[String], message:Message):Option[AppContext] = {
    message match {
      case HasToken(token) =>
        ActiveConnections.get(token) match {
          case Some(connection) => 
            val app = connection.appContext
            if (app.isEmpty)
              log.error("connection found for %s, but missing no application set", message.toJson)
            app
          case None =>
            log.error("appFor: token %s not found, closing the client connection", token)
            // LATER get rid of delay, it's a temp measure so that pre 0.2 (27.10.2009) clients don't re-request
            val app = new ClosedApp(ActiveConnections.createConnection(), 2000)
            Some(app)
        }
      case HasStart(start) => 
        val connection = ActiveConnections.createConnection()
        val app = createAppContext(syncPath, message, connection)
        connection.appContext = Some(app)
        Some(app)
      case _ =>  
        log.error("message contains neither #token nor #start : %s", message.toJson)
        None
    }
  }
  
}
