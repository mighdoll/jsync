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

import _root_.net.liftweb.common.Box
import _root_.net.liftweb.common.Full
import _root_.net.liftweb.http._
import collection._
import ActiveConnections._
import net.lag.logging.Logger
import com.digiting.util.LogHelper

/**
 * Hooks to connect sync processing to Lift http requests
 */
object SyncRequestApi extends LogHelper {
  implicit val log = Logger("SyncRequestApi")
  
  // attach to the stateful dispatch (CONSIDER using stateless dispatch)
  def dispatch:PartialFunction[Req, () => Box[LiftResponse]] = {
    case req @ Req("sync" :: Nil, _, PostRequest) =>
      () => sync(req)
    case req @ Req("admin" :: "sync" :: Nil, _, PostRequest) =>
      () => sync(req)
    case req @ Req("test" :: "sync" :: Nil, _, PostRequest) =>
      () => sync(req)
    case Req("generatedModels" :: Nil, _, GetRequest) =>
      () => jsModels
  }
  
  private def error[T](message:String, params:String*):Option[T] = {
    log.error(message, params:_*)
    None
  }
  
  private def jsModels:Box[LiftResponse] = {
    val js = JavascriptModels.generate
    
    Full(InMemoryResponse(js.getBytes, 
      ("Content-Type" -> "application/x-javascript") :: Nil, Nil, 200))
  }
  
  
  private def reqBody(req:Req):Option[String] = {
    req.body map {new String(_)} 
  }

  /** receive an incoming message from lift  */
  private def sync(req:Req):Box[LiftResponse] = {
    log.trace("sync request received: %s", req)
    
    val body = req.body map {new String(_)} getOrElse ""
    val responseEither:Either[ConnectionError, String] = {
      try {
        Applications.deliver(req.path.partPath, body) match {
          case Right(connection) =>
            connection.takeResponse match {
              case Some(response) =>
                Right(response)
              case _ =>
                ConnectionError.leftError(500, "odd, no response at all for body : %s", body)
            }
          case Left(left) => Left(left)
        }
      } catch {
        case e:Exception => 
          ConnectionError.leftError(500, "exception processing request %s \n%e", req.toString, e.getStackTraceString)
      }
    }
    
    responseEither match {
      case Left(conError) => 
        log.trace("sync() error: %s", conError)
        Full(InMemoryResponse(conError.message.getBytes,
          ("Content-Type" -> "text/html") :: Nil, Nil, conError.errorCode))     
      case Right(response) => 
        log.trace("sync() response: %s", response)
        Full(InMemoryResponse(response.getBytes,
          ("Content-Type" -> "application/json") :: Nil, Nil, 200))     
    }      

  }
  
 
}