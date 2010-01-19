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
import scala.actors.Actor

/**
 * Hooks to connect sync processing to Lift http requests
 */
object SyncRequestApi extends LogHelper {
  val log = Logger("SyncRequestApi")
  
  // attach to the stateful dispatch (CONSIDER using stateless dispatch)
  def dispatch:PartialFunction[Req, () => Box[LiftResponse]] = {
    case req @ Req("sync" :: Nil, _, PostRequest) =>
      () => sync(req)
    case req @ Req("admin" :: "sync" :: Nil, _, PostRequest) =>
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
    try {
      log.trace("sync request received: %s", req)
      
      val response = 
        for {
          bodyArray <- req.body orElse 
            err("empty body") 
          body = new String(bodyArray)
          app <- Applications.deliver(req.path.partPath, body) orElse 
            err("app not found for body: %s", body)
      	  response <- app.connection.takeResponse orElse 
      	  	err("odd, no response at all for body: %s", body)
        } yield {
          log.trace("sync() response: %s", response)
          InMemoryResponse(response.getBytes,
	          ("Content-Type" -> "application/json") :: Nil, Nil, 200)     
        }
      
     response orElse {
       notUnderstood(req)
     }
     
    } catch {
      case e:Exception => 
        log.error(e, "exception processing request %s", req)
	    Full(InMemoryResponse("[]".getBytes,
	                         ("Content-Type" -> "application/json") :: Nil, Nil, 500))        
    }
  }
  
  private def notUnderstood(req:Req):Box[LiftResponse] = {
    log.error("request not understood: %s", req.body)
	  Full(InMemoryResponse("[]".getBytes,
        ("Content-Type" -> "application/json") :: Nil, Nil, 500))
  }
  
}