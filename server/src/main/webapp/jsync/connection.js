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
var $sync = $sync || {};      // namespace

/** Maintain a network connection to a server feed using the jsonsync protocol.
 * 
 * The sync connection is logically a single bidirectional stream of data.   
 * However, the sync stream data may be carried over a series of http connections, 
 * or even in parallel over multiple connections simultaneously.  
 * 
 * @param {String} feedUrl - url of the jsonsync protocol endpoint
 * @param {Object} params -  optional { 
 *                  testMode: true/false, 
 *                  connected: function,
 *                  failFn: function,   // called when any http request fails (useful for tests)
 *                  appVersion: string, 
 *                  requestTimeout: int, // msec to wait on ajax requests
 *                  authorization: token (string) }
 */
$sync.connect = function(feedUrl, params) {
//  var log = oops;   // triggers an internal firefox bug on 3.5: internal error cannot access optimized closure
  var log = $log.getLogger("connect");     
  var self = {};
  var sentTransaction = 0;              // protocol sequence number sent
  var testModeOut;                      // output for test mode
  var subscriptions;                    // object trees we're mirroring from the server
  var sendWhenConnected = [];           // queue of messages to send once the logical connection is open
  var longPollEnabled =                 // long polling is enabled by default
    (!window.location.search.match(/[?&]longpoll=false/));
  var minActiveRequests = 1;            // long poll should keep this many requests active 
  var requestsActive = 0;               // current number of active requests
  var consecutiveTimeouts = 0;            // number of failures in a row from the server
  var backoffDelay =                    // exponential backoff rate for server requests
    [100,500,1000,10000,30000,120000,600000];
  var receive = $sync.receive(self);    // handle incoming messages

  /** open a connection to the server */
  function init() {
    if (params && params.testMode) {
      self.connectionToken = "localTestModeToken";
      connected([]);
    } else {
      start(); // connect            
    }
  }
  
  /** close the connection.  Any subsequent messages received will be dropped.  
   * pass {ignoreMessages:true} in params to silence warnings if future messages are dropped 
   * @param {Object} params
   */
  self.close = function(params) {   
    self.isClosed = params !== undefined ? params : true;
  };
  
  self.requestsActive = function() { 
    return requestsActive;
  }
  
  self.isClosed = false;                 // true or object if this connection has been closed
  self.connectionToken = undefined;      // password for this connection 

  /** @return true if we've an active sync connection to the server */
  self.isConnected = function() {
    return self.connectionToken !== undefined;
  };
  
    
  /** accept a json message for testing as if it came from the server */
  self.testReceiveMessages = function(messages) {
    receive.receiveMessages(messages);
  };
  
  /** send all modified objects to the server */
  self.sendModified = function(changeSet) {
    var delta;                      // jsonSync message fragment for a property change
    var edit;                       // jsonSync message fragment for a collection change
    var target;                     // target of the change
    var changeType;                 // type of change
    var xact;                       // change we're sending
    var created;                    // new object created
    var outgoing;                   // modified object for sending new objects

    if (changeSet.length === 0) return;    
    
    xact = startNextSendXact();     
    $.each(changeSet, function(index, change) {      
      target = change.target;
      changeType = change.changeType;
//      $debug.log("sendModified, change: " + change);
      if (changeType === "create") {        
        if (target.$partition !== '.implicit') {// don't send 'well known' objects 
          outgoing = outgoingSyncable(target);
          xact.push(outgoing);
//          $debug.log(".outgoing: " + JSON.stringify(outgoing));
        }
      } else if (changeType === "property") {
        delta = {};
        delta.$id = target.$id;
        delta.$partition = target.$partition;
        delta[change.property] = outgoingValue(target[change.property]);
        xact.push(delta);
//        $debug.log(".sending delta: " + JSON.stringify(delta));
      } else if (changeType === "edit") {
        edit = {};
        $debug.assert(target.$partition !== undefined);
        edit['#edit'] = {$id:target.$id, "$partition":target.$partition};
        if (typeof(change.put) !== 'undefined') {
          edit.put = {$id:change.put.$id, "$partition":change.put.$partition};
        } else if (typeof(change.clear) !== 'undefined') {
          edit.clear = true;
        } else if (typeof(change.insertAt) !== 'undefined') {
          edit.insertAt = [{
            at:change.insertAt.index, 
            elem:{
              $id:change.insertAt.elem.$id,
              $partition:change.insertAt.elem.$partition
            }
          }];
        } else if (typeof(change.move) !=='undefined') {
          edit.move = {
            from:change.move.from,
            to:change.move.to
          };
        } else if (typeof(change.removeAt) !== 'undefined') {
          edit.removeAt = change.removeAt;
        } else {
          $debug.error("sendModified doesn't know how to send change:" + change);     
        }
        
        xact.push(edit);
//      $debug.log(".sending #edit: " + JSON.stringify(edit));
      } else {
         $debug.error("sendModified doesn't know how to send change: " + change);     
      }
    });
    if (params && params.testMode)
      testModeOut = xact;
    else
      send(xact, receive.receiveMessages);
  };


  
  /** copy and convert a syncable to a form suitable for sending over the wire 
   * convert references to $ref objects, and add the kind property
   * @param {Object} syncable
   */
  function outgoingSyncable(syncable) {
    // add $kind as local property, stringify will skip it if it's just in the prototype
    var toSend = {$kind: syncable.$kind };
    var value;
    
    for (var property in syncable) {
      if (syncable.hasOwnProperty(property) && property[0] != '_') {
        value = syncable[property];
        if (typeof value !== 'function' &&
            value !== null &&
            value !== undefined) {
          // modify properties for sending (replace refs with $ref objects)
          toSend[property] = outgoingValue(syncable[property]);
        }
      }
    }
    return toSend;
  }

  /** convert property values into a form suitable for sending over the wire. 
   * replace object references to syncable objects with $ref objects.  */  
  function outgoingValue(value) {
//  if (typeof(value) !== 'function') {
//    $debug.log("outgoing property: " + property + " = " + value + " isSyncable:" + $sync.manager.isSyncable(value));          
//  }
    if ($sync.manager.isSyncable(value)) {
      return {$ref: {$partition: value.$partition, $id:value.$id}};
    } 
    
    return value;
  }
  
  /** If testMode is true, return the last message string that
    * would normally be sent to the network */
  self.testOutput = function() {
    return testModeOut;
  };
    
  /** Subscribe to server propagated changes on a set of objects
   * The caller can watch for changes on the returned subscription object.
   * When the root field is filled in, the subscription request has been
   * filled.
   *
   * @param name - well known name shared by the server to identify this
   *  tree of subscribed objects
   * @param watchFunc - applied to the subscribed root object when it arrives
   *  (and again in the unlikely event that the root is subsequently changed)
   * @return a subscription object: {name: "string", root: rootObject}
   */
  self.subscribe = function(name, partition, watchFunc) {
    var sub;
    $sync.manager.withPartition(self.connectionToken, function() {
      sub = $sync.subscription();
    });
    
    sub.name = name;
    sub.inPartition = partition;
    subscriptions.put(sub);
    if (watchFunc) {
      $sync.observation.watch(sub, function(changes) {
        watchFunc(changes.target.root);
      });
    }

    return sub;
    
 // LATER delete items from the subscriptions list to unsubscribe
  };
        
  /** start connection to the server */
  function start() {
//    $log.info("starting connection to: " + feedUrl);
    var xact = startNextSendXact();
    var startMessage = {
      '#start': {
        authorization: params.authorization || "",
        appVersion: params.appVersion || $sync.defaultAppVersion || "",
        protocolVersion: $sync.protocolVersion
      }
    };
    xact.push(startMessage);
    sendNow(xact, connected);
  }
  
  /** called when the sync connection to the server is opened
   * -- we've heard a response from the server in response to our initial connection attempt */
  function connected(data) {
//    $log.info("connected");
    
    receive.receiveMessages(data); // parse any data the server has waiting for us
    $debug.assert(self.connectionToken !== undefined);
    $sync.manager.registerConnection(self, self.connectionToken);
    
    $sync.manager.withNewIdentity({
      partition: ".implicit",   
      $id: "subscriptions"
    }, function() {
      subscriptions = $sync.set();
    });
    
    $sync.util.arrayFind(sendWhenConnected, function(queued) {
      $debug.warn("sendWhenConnected: is this still used?");
      sendNow(queued.xact, queued.successFn);
    });
    if (params && params.connected) // notify anyone who's listening that we're now connected
      params.connected(self);
    
  }

  /** continually keep a request outstanding to the server */
  function longPoll() {
    
    if (!longPollEnabled || !self.connectionToken || self.isClosed) return;
    
    if (requestsActive < minActiveRequests) {      
      setTimeout(pollServer, retryDelay());  // use timeout so our stack doesn't grow ever deeper
    }
    
    /** exponential backoff for failed requests */
    function retryDelay() {
      var delay, backoffDex, fixedDelay = 10;
      
      if (consecutiveTimeouts === 0) {
        return fixedDelay;
      }
      
      backoffDex = Math.min(consecutiveTimeouts, backoffDelay.length - 1);
      delay = fixedDelay + (Math.random() * backoffDelay[backoffDex]);      
      return Math.round(delay);
    }
    
    function pollServer() {
      send([], receive.receiveMessages);
    }
  }
  
  /** send a message up to the server (or queue it if we're not connected yet */
  function send(xact, successFn) {
    if (!self.isConnected()) {
      sendWhenConnected.push({
        xact: xact,
        successFn: successFn
      });
    } else {
      sendNow(xact, successFn);
    }
  }
  
  /** send message up to the server
   * (for now, we send each request over a separate http request.  could optimize this LATER) */
  function sendNow(xact, successFn) {
    if (self.connectionToken !== undefined) {
      xact.push({"#token": self.connectionToken});
    }
    
    var xactStr = JSON.stringify(xact);
//    $debug.log("sending xact to server: " + xactStr);
    requestsActive += 1;
    $.ajax({
      url: feedUrl,
      type: "POST",
      dataType: "json",
      contentType: "application/json",
      success: success,
      error: failed,
      data: xactStr,
      timeout: params.requestTimeout || 20000
    });
    
    function success() {
      consecutiveTimeouts = 0;
      requestsActive -= 1;
      successFn.apply(this, arguments);
      longPoll();     
    }
    
    /** called if there's a an error with the http request 
     * (also called, I think, if there's an error with json format in the result */
    function failed(xmlHttpRequest, textStatus, errorThrown) {
//      log.warn("$sync protocol request failed: ", xmlHttpRequest.status, xmlHttpRequest.statusText);      // causes this error: (only when run in the test suite though..) uncaught exception: [Exception... "Component returned failure code: 0x80040111 (NS_ERROR_NOT_AVAILABLE) [nsIXMLHttpRequest.status]" nsresult: "0x80040111 (NS_ERROR_NOT_AVAILABLE)" location: "JS frame :: http://localhost:8080/jsync/connection.js :: failed :: line 334" data: no]
      if (textStatus === "timeout") {
        consecutiveTimeouts += 1;
        requestsActive -= 1;
        longPoll();     
      } else {
        self.close();
      }         
      params.failFn && params.failFn(self, this, xmlHttpRequest, textStatus, errorThrown);       
    }    
  }

    
  /** create the next transaction to send to the client */
  function startNextSendXact() {
    var xact = [];
    xact.push({ '#transaction': sentTransaction++ });
    return xact;
  }
  

  init();
    
  return self;
};


/*
 * LATER support websocket if available
 */
