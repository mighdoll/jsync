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
import scala.collection.mutable.ListBuffer
import com.digiting.util.TryCast.matchString

object Applications extends LogHelper {
  implicit val log = Logger("Applications")

  /** deliver a message to the appropriate registered application (asynchronously). 
   * returns the application that is processing the message */
  def deliver(syncPath:List[String], messageBody:String):Either[ConnectionError, Connection] = {
    var foundError:Option[ConnectionError] = None
    var foundApps = new ListBuffer[AppContext]
    
    ParseMessage.parse(messageBody) find {message =>
      getAppFor(syncPath, message) match {
        case Right(app) => 
          foundApps += app
          log.ifTrace("delivering to app" + app.connection.debugId + "  messsage to: " +
             syncPath.mkString("/") +  "  message empty:" + message.isEmpty + ": " + messageBody)
          if (!message.isEmpty)     
            app receiveMessage(message)
          app
          false // continue delivering messages in this message set
        case Left(err) => 
          foundError = Some(err)
          true  // don't deliver any more messages from this message set (consider this SOMEDAY)
      }
    }

    // return error if we found one, otherwise grab the connection from any app (currently each connection has only one app anyway)
    foundError match {
      case Some(err) => Left(err)
      case _ => 
        foundApps find {_ => true} map {_.connection} match {
          case Some(connection) => Right(connection)
          case _ =>
            messageBody match {
              case "" => 
                ConnectionError.leftError(400, "empty request received")
              case _ =>
                ConnectionError.leftError(500, "Odd that we didn't have an error on an app for message: %s", messageBody)
            }
        }
    }
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
  
  private def createAppContext(syncPath:List[String], startParams:StartParameters, message:Message, connection:Connection):AppContext = {
    appContextCreators.find(creator =>
      creator.isDefinedAt(syncPath, message,connection)
    ) match {
      case Some(creator) =>
        creator(syncPath, message, connection)
      case None =>
        log.warning("using generic AppContext, because no application context is defined for message: %s, sent to: %s",
          message.toJson, syncPath mkString("/"))
        new GenericAppContext(connection)
    }
  }
  
  
  object HasToken {
    def unapply(message:Message):Option[String] = 
      message.findControlProperty("#token") flatMap matchString
  }
  
  import com.digiting.sync.JsonObject.JsonMap
  import net.liftweb.json.JsonParser
    
  object HasStart {
    val log = Logger("HasStart")
    implicit val formats = net.liftweb.json.DefaultFormats
    def unapply(message:Message):Option[StartParameters] = 
      message.findControlProperty("#start") flatMap {
        _ match {
          case map:Map[_,_] => 
            // experimenting with lift-json a bit to extract StartParameters.  TODO get rid of this when we move to lift-json overall
            val jsonMap = map.asInstanceOf[JsonMap]
            val jsonString = JsonUtil.toJson(jsonMap)
            val parsed = JsonParser.parse(jsonString)
            val start = parsed.extract[StartParameters]
            Some(start)
          case x => 
            log.error("unexpected target of #start: ", x)
            None
        }
      } 
  }
  
  case class StartParameters(authorization:String, appVersion:String, protocolVersion:String)
  
    
  /** find an app context for the incoming message, creating a new app context if necessary */
  private def getAppFor(syncPath:List[String], message:Message):Either[ConnectionError, AppContext] = {
    message match {
      case HasToken(token) =>
        ActiveConnections.get(token) match {
          case Some(connection) => 
            val appOpt = connection.appContext
            appOpt match {
              case Some(app) => Right(app)
              case None => 
                val msg = String.format("connection found for %s, but missing an application!", message.toJson)
                err(msg)
                Left(ConnectionError(500, msg))                
            }
          case None =>
            ConnectionError.leftError(404, "token %s not found, closing the client connection", token)
        }
      case HasStart(start) => 
        log.trace("#start parameters: %s", start)
        if (start.protocolVersion != ProtocolVersion.version) {
          val msg = String.format("client protocol version: %s doesn't match server version: %s", start.protocolVersion, ProtocolVersion.version)
          err(msg)
          Left(ConnectionError(400, msg))
        } else {
          val connection = ActiveConnections.createConnection()
          val app = createAppContext(syncPath, start, message, connection)
          if (app.appVersion != start.appVersion) {
            connection.close()
            ConnectionError.leftError(400, "client appVersion(%s) doesn't match server appVersion(%s)", 
              start.appVersion, app.appVersion)            
          } else {
            connection.appContext = Some(app)
            Right(app)
          }
        }
      case _ => 
        ConnectionError.leftError(400, "message contains neither #token nor #start %s ", message.toJson)
    }
  }
  
}
