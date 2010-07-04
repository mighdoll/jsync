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
package com.digiting.sync.test

import com.digiting.util.LogHelper
import net.lag.logging.Logger
import com.digiting.sync.Applications
import com.digiting.sync.AppContext
import com.digiting.sync.ResponseManager.AwaitResponse
import com.digiting.util.TryCast.matchString
import com.digiting.sync.Message.toJsonMap
import com.digiting.sync.Message.insertAts
import com.digiting.sync.Message.insertAtToJsonMap
import com.digiting.sync.JsonObject.JsonMap
import JsonObject.ImmutableJsonMap
import SyncManager.withPartition
import com.digiting.sync.syncable._
import JsonMapParser.Reference
import com.digiting.sync.testServer.TestApplication
import com.digiting.util.Configuration


object ProtocolFixture extends LogHelper {
  val log = Logger("ProtocolFixture")
  
  /** Send a raw json-sync message to the test application server.  The verifyFn should
   * return Some() for success or failure, and None to wait for additional messages 
   * to come from the server.  
   */
  def sendTestMessage[T](msg:String, verifyFn: (String)=>Option[T]):Option[T] = {
    log.trace("sending message: %s", msg)
    Applications.deliver("test" :: "sync":: Nil, msg).right.toOption orElse {
      err("no app found") 
    } flatMap {connection =>      
      repeatUntil (3, checkResponse(connection, verifyFn))
    }
  }

  /** Call a service in the test application server.  return the response from the app server.
    * 
    * Acts as a protocol client using manually constructed and parsed messages rather
    * than the AppContext framework.
    */
  def callService(serviceName:String, paramsFn: => List[Syncable]):Option[JsonMap] = {
    val (messageStr, serviceCall) = withPartition(TransientPartition) {
      makeServiceCallMessage(serviceName, paramsFn)
    }
    
    sendTestMessage(messageStr, getServiceReply(serviceCall)) 
  }
  
  // manually extract a reply edit to a ServiceCall
  private def getServiceReply(serviceCall:ServiceCall[Syncable])(messageStr:String):Option[JsonMap] = {
    var syncJsonMaps =
      for {
        message <- ParseMessage.parse(messageStr)
        syncObj <- message.syncs 
        a = log.trace("considering reply sync: %s", syncObj)
      } yield syncObj
    
    var resultIds = 
      for {
        syncObj <- syncJsonMaps
        id <- syncObj get("$id") if id == serviceCall.id.instanceId.id
          a = trace("found service call: %s", syncObj)
        result <- syncObj get("results")
          a = trace("found results: %s", result)      
        resultRefId <- Reference.unapply(result)
          a = trace("found resultRefId: %s", resultRefId)      
      } yield resultRefId
    
    var resultIdOpt = resultIds find {_ => true}
    
    val resultMapOpt = 
      resultIdOpt flatMap {resultId =>
        var resultMaps = 
          for {
            syncObjMap <- syncJsonMaps
            id <- syncObjMap get("$id") if id == resultId.instanceId.id
          } yield {
            log.trace("found result: %s", syncObjMap)            
            syncObjMap
          }
        resultMaps find {_ => true} 
      }
    
    log.trace("resultMap: %s", resultMapOpt)      
    resultMapOpt
  }
  
  // manually construct a Message containing a service call    
  private def makeServiceCallMessage(serviceName:String, parameters:Iterable[Syncable]):(String, ServiceCall[Syncable]) = {
    
    // sync service call    
    val syncs = new collection.mutable.ListBuffer[JsonMap]    
    val serviceCall = new ServiceCall[Syncable]
    val paramSeq = new SyncableSeq[Syncable]
    serviceCall.parameters = paramSeq
    syncs += toJsonMap(serviceCall)
    
    // sync parameters
    syncs += toJsonMap(paramSeq)
    
    val parameterRefsObs = parameters flatMap {SyncableAccessor.observableReferences(_) }
    val parameterRefs = parameterRefsObs map {_.asInstanceOf[Syncable]}
    val deepParameters = new collection.mutable.HashSet[Syncable]()
    deepParameters ++= parameters
    deepParameters ++= parameterRefs
    val paramJsonMaps = deepParameters map toJsonMap toList ;
    syncs ++= paramJsonMaps
    val parameterIds = parameters map {_.fullId}

    var serviceQueueId = SyncableId(PartitionId(".implicit"), serviceName)
    val edits = 
      insertAts(paramSeq.fullId, parameterIds) :::
      insertAtToJsonMap(serviceQueueId, serviceCall.fullId, 0) :: Nil   

    // controls, edits, syncs => message
    val startParams = ImmutableJsonMap(
      "authorization" -> "",
      "protocolVersion" -> ProtocolVersion.version,
      "appVersion" -> TestApplication.appVersion)
    val controls = ImmutableJsonMap("#start" -> startParams) :: Nil
    val message = new Message(0, controls, edits, syncs.toList)
    
    (message.toJson, serviceCall)    
  }
  
  private def clientPartition = {
    val partitionName = "simulatedClient"
    Partitions.get(partitionName) getOrElse new RamPartition(partitionName)
  }
  
  private def checkResponse[T](connection:Connection, verifyFn: (String)=>Option[T]):Option[T] = {
    val timeout = Configuration.getInt("ProtocolFixture-timeout", 200) 

    for {
      responseAny <- connection.responses !?(timeout, AwaitResponse(37)) orElse
        err("unexpected None from AwaitResponse")
      response <- matchString(responseAny)
      a = log.trace("gotResponse: %s", response)      
      result <- verifyFn(response)
    } yield {
      result
    }
  }

  /** repeat fn until it returns Some(), up to repeats times */
  private def repeatUntil[T](repeats:Int, fn: =>Option[T]):Option[T] = {
    var count = 0
    var done = false
    var result:Option[T] = None
    
    while (count < repeats && !done) {
      result = fn 
      result map { _ =>
        done = true         
      } orElse { 
        count += 1
        None
      }
    }
    result
  }
  
}
