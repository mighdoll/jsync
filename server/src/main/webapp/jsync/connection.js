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
 * @param feedUrl - url of the jsonsync protocol endpoint
 * @param params - optional options { 
 *                  testMode: true/false, 
 *                  connected: function,
 *                  authorization: token }
 */
$sync.connect = function(feedUrl, params) {
  var self = {};
  var receivedTransaction = -1;         // protocol sequence number received
  var sentTransaction = 0;              // protocol sequence number sent
  var testModeOut;                      // output for test mode
  var connectionToken = undefined;      // password for this connection 
  var subscriptions;                    // object trees we're mirroring from the server
  var received = [];                    // queue of received messages (possibly out of sequence)
  var lastProcessed = $sync.util.now(); // last time a message was received
  var missedMessageTimeout = 500000;    // buffer out of order server messages for this many msec
  var sendWhenConnected = [];           // queue of messages to send once the logical connection is open
  var isClosed = false;                 // true if this connection has been closed
  var longPollEnabled =                 // long polling is enabled by default
    (!window.location.search.match(/[?&]longpoll=false/));
  var minActiveRequests = 1;            // long poll should keep this many requests active 
  var requestsActive = 0;               // current number of active requests
  var consecutiveFailed = 0;            // number of failures in a row from the server
  var backoffDelay =                    // exponential backoff rate for server requests
    [100,500,1000,10000,30000,120000,600000];

  /** open a connection to the server */
  function init() {
    if (params && params.testMode) {
      connectionToken = "localTestModeToken";
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
    isClosed = params;  
  }

  /** @return true if we've an active sync connection to the server */
  self.isConnected = function() {
    return connectionToken !== undefined;
  }
  
    
  /** accept a json message for testing as if it came from the server */
  self.testReceiveMessages = function(messages) {
    receiveMessages(messages);
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
    changeSet.eachCheck(function(change) {      
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
        delta.id = target.id;
        delta.$partition = target.$partition;
        delta[change.property] = outgoingValue(target[change.property]);
        xact.push(delta);
//        $debug.log(".sending delta: " + JSON.stringify(delta));
      } else if (changeType === "edit") {
        edit = {};
        $debug.assert(target.$partition != undefined);
        edit['#edit'] = {id:target.id, "$partition":target.$partition};
        if (typeof(change.put) !== 'undefined') {
          edit.put = {id:change.put.id, "$partition":change.put.$partition};
        } else if (typeof(change.clear) !== 'undefined') {
          edit.clear = true;
        } else if (typeof(change.insertAt) !== 'undefined') {
          edit.insertAt = {
            at:change.insertAt.index, 
            elem:{
              id:change.insertAt.elem.id,
              $partition:change.insertAt.elem.$partition
            }
          };
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
      send(xact, receiveMessages);
  };


  
  /** copy and convert a syncable to a form suitable for sending over the wire 
   * convert references to $ref objects, and add the kind property
   * @param {Object} syncable
   */
  function outgoingSyncable(syncable) {
    // add kind as local property, stringify will skip it if it's just in the prototype
    var toSend = {kind: syncable.kind };
    var value;
    
    for (property in syncable) {
      if (syncable.hasOwnProperty(property) && property[0] != '_') {
        value = syncable[property];
        if (typeof value !== 'function' &&
        value != null &&
        value != undefined) {
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
      return {$ref: {$partition: value.$partition, id:value.id}};
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
    $sync.manager.withPartition(connectionToken, function() {
      sub = $sync.subscription();
    });
    
    sub.name = name;
    sub.inPartition = partition;
    subscriptions.put(sub);
    if (watchFunc) {
      $sync.observation.watch(sub, function(changes) {
        watchFunc(changes.target.root)
      });
    }

    return sub;
    
 // LATER delete items from the subscriptions list to unsubscribe
  };
        
  /** start connection to the server */
  function start() {
//    $debug.log("starting connection to: " + feedUrl);
    var xact = startNextSendXact();
    xact.push({'#start': true });
    params.authorization && xact.push({'#authorization':params.authorization});
    sendNow(xact, connected);
  }
  
  /** called when the sync connection to the server is opened
   * -- we've heard a response from the server in response to our initial connection attempt */
  function connected(data) {
//    $log.info("connected");
    
    receiveMessages(data); // parse any data the server has waiting for us
    $debug.assert(connectionToken !== undefined);
    $sync.manager.registerConnection(self, connectionToken);
    
    $sync.manager.withNewIdentity({
      partition: ".implicit",   
      id: "subscriptions"
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
    
    if (!longPollEnabled || !connectionToken || isClosed) return;
    
    if (requestsActive < minActiveRequests) {      
      setTimeout(pollServer, retryDelay());  // use timeout so our stack doesn't grow ever deeper
    }
    
    /** exponential backoff for failed requests */
    function retryDelay() {
      var delay, backoffDex, fixedDelay = 10;
      
      if (consecutiveFailed == 0) {
        return fixedDelay;
      }
      
      backoffDex = Math.min(consecutiveFailed, backoffDelay.length - 1);
      delay = fixedDelay + (Math.random() * backoffDelay[backoffDex]);      
      return Math.round(delay);
    }
    
    function pollServer() {
      send([], receiveMessages);
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
    if (connectionToken !== undefined) {
      xact.push({"#token": connectionToken});
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
      data: xactStr
    });
    
    function success() {
      consecutiveFailed = 0;
      requestsActive -= 1;
      successFn.apply(this, arguments);
      longPoll();     
    }
    
    function failed() {
      consecutiveFailed += 1;
      requestsActive -= 1;
      syncFail.apply(this, arguments);       
      longPoll();     
    }    
  }

  /** called if there's a an error with the http request 
   * (also called, I think, if there's an error with json format in the result */
  function syncFail(XMLHttpRequest, textStatus, errorThrown) {
    $debug.log("$sync protocol request failed: " + textStatus);
  }
    
  /** create the next transaction to send to the client */
  function startNextSendXact() {
    var xact = [];
    xact.push({ '#transaction': sentTransaction++ });
    return xact;
  };
  
  /** accept an array of messages from the network, process immediately 
   * if we have next message in the protocol transaction # sequence, othewise 
   * queue it until new message comes along.  */
  function receiveMessages(messages) {
    var msgDex;
    if (messages.length === 0) 
      return;
    
    if (isClosed) {
      if (!isClosed.ignoreMessages) {
        $debug.warn("connection (" + connectionToken +
        ") is closed.  Dropping these messages: " +
        JSON.stringify(messages));
      }
      return;
    }
    
    for (msgDex = 0; msgDex < messages.length; msgDex++) {
      received.push(messages[msgDex]);
    }
    
    processReceived();
  }
  
  /** process received messages in sequence */
  function processReceived() {
    var message;
    
    while ((message = takeNextMessage())) {
      parseMessage(message);
    }
  }
  
  /** 
   * @return the next sequentially received protocol message, or undefined
   * if the next message hasn't arrived yet. */
  function takeNextMessage() {
    var i, transactionNum, message;
    
    if (received.length === 0)
      return undefined;
    
    for (i = 0; i < received.length; i++) {
      message = received[i];
      transactionNum = findTransactionNum(message);
      if (transactionNum === receivedTransaction + 1) {
        receivedTransaction = transactionNum;
        received.splice(i, 1);
        lastProcessed = $sync.util.now();
        return message;
      }
    }
    if ($sync.util.now() - lastProcessed > missedMessageTimeout) {
      // SOON, reset the connection, etc.
      $debug.error("waited too long for message: " + (receivedTransaction + 1));
    } else {
      // LATER set a timer to reset the connection in case it's never received
      $debug.log("connection.takeNextMessage() waiting for out of order transaction: " + (receivedTransaction + 1));
    }
    return undefined;
  }
  
  /** find transaction # object in a message (normally it comes first, but scan just in case */
  function findTransactionNum(jsonMessage) {
    var number;

    number = $sync.util.arrayFind(jsonMessage, function(obj) {
      return obj['#transaction'];   // halts if we found it 
    });

    if (number === undefined)
      $debug.error("missing #transaction in: " + JSON.stringify(jsonMessage));
    return number;
  }

  /** handle a message full of object changes from the server.
   * 1) instantiate new objects, enter them into the map
   * 2) update object references in received objects
   * 3) update properties of existing objects based on data received
   * 4) update collections
   * 5) send notifications to observers
   * 
   * @param message -- array of jsync javascript objects received in a jsync message
   */
  function parseMessage(message) {
    var i, obj, toUpdate = [], toEdit = [], incomingTransaction;
    
//    $log.debug("parseMessage: ", message);
    if (message.length === 0) 
      return;
    
    // queue outgoing notifications until we're done processing
    $sync.observation.pause();
    $sync.observation.serverMutator();
    
    // 1st pass through through the contents of the entire transaction
    for (i = 0; i < message.length; i++) {
      obj = message[i];
      if (obj == undefined) {
        // empty elements oughtn't be in the stream 
        $debug.warn("empty object in JSON stream");
      }
      else if (obj.hasOwnProperty("#edit")) {
        // queue collection changes to handle a little later
        toEdit.push(obj);
      }
      else if (obj.hasOwnProperty("#reset")) {
        $debug.error("#reset not yet implemented");
      }
      else if (obj.hasOwnProperty("#transaction")) {
      }
      else if (obj.hasOwnProperty("#token")) {
        $debug.assert(connectionToken === undefined);
        connectionToken = obj["#token"];
      }
      else if (hasHashProperty(obj)) {
        $debug.error("unsupported #property in: " + JSON.stringify(obj));
      }
      else {
        // create and register objects heretofore unknown
        if (!$sync.manager.contains(obj.$partition, obj.id)) {
          $sync.manager.createRaw(obj);
        }
        // update local objects with the received data 
        toUpdate.push(obj);
      }
    }
    
    // process $ref references.  (all received objects should now be in the map)
    for (i = 0; i < message.length; i++) {
      obj = message[i];
      resolveRefs(obj);
    }
    
    // update objects by copying received data
    for (i = 0; i < toUpdate.length; i++) {
      obj = toUpdate[i];
      $sync.manager.update(obj);
    }
    
    // preform fancier updates e.g. collection membership changes
    for (i = 0; i < toEdit.length; i++) {
      obj = toEdit[i];
      $sync.manager.collectionEdit(obj);
    }
    
    // transaction is complete:  tell everyone what we've done
    $sync.observation.popMutator();
    $sync.observation.endPause();
  }

  /** @return true if an object contains a property starting with '#'.
   * properties starting with '#' are resevered for jsync control messages
   */   
  function hasHashProperty(obj) {
    for (prop in obj) {
      if (prop[0] === '#')
        return true;
    }
    return false;
  }
     
  /** replace any reference property value with the referenced object.
     * references look like this-  val:{$ref: 17}. which would be replaced
     * with the syncable object #17, e.g. val:{id:17, kind:"person", name:"fred"} */
  function resolveRefs(obj) {
    var prop, arrayDex, arrayVal;

    if (typeof obj !== 'object') {
      $debug.warn("resolveRefs can't resolve obj: " + obj); // shouldn't happen
      return obj;
    }
        
    if ($sync.util.isArray(obj)) {
      // recurse on array values that are objects or sub-arrays
      for (arrayDex = 0; arrayDex < obj.length; arrayDex++) {
        arrayVal = obj[arrayDex];
        if (typeof arrayVal === 'object') {
          obj[arrayDex] = resolveRefs(arrayVal);
        }
      }
    } else {
      // recurse on object properties that have object or array values
      for (prop in obj) {
        if (typeof obj[prop] === 'object' && obj[prop]) {
          obj[prop] = resolveRefs(obj[prop]);
        }
      }
    }
        
    // return either the object reference or the $ref translated
    // into a real object reference
    if (obj.$ref) {
      return $sync.manager.get(obj.$ref.$partition, obj.$ref.id);
    }
    return obj;
  }

  init();
    
  return self;
};


/*
 * LATER support websocket if available
 */
