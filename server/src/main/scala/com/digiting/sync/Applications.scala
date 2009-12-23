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
import net.lag.logging.Level._
import scala.collection.mutable
import Receiver.ReceiveMessage

object Applications {
  var log = Logger("Applications")

  private def error[T](message:String, params:String*):Option[T] = {
    log.error(message, params:_*)
    None
  }

  /** deliver a message to a registered application, 
   * return the application */
  def deliver(syncPath:List[String], messageBody:String):Option[AppContext] = {
    var found:Option[AppContext] = None
	for {
	  message <- ParseMessage.parse(messageBody) orElse
        error("message not parsed: %s", messageBody)
      app <- getAppFor(syncPath, message) orElse
		error("app not found for: %s", message.toString)} {
	  
	  log.logLazy(TRACE, {"delivering to app" + app.connection.debugId + "  messsage to: " +
                       syncPath.mkString("/") +  "  message empty:" + message.isEmpty + ": " + messageBody})
      if (!message.isEmpty) 	  
	    app.connection.receiver ! ReceiveMessage(message)
      found = Some(app)
	}
    found
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
	    log.logLazy(WARNING, ("using default context, because no application context is defined for: message" + message.toJson))
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
            // need to reset the client, etc.
            log.error("appFor: token %s not found, need to reset the client connection. Not yet implemented", token)
            None
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
