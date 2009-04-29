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
 * @param params - optional options { testMode: true/false
 */
$sync.connect = function(feedUrl, params) {
  var self = {};
  var receivedTransaction = -1; // protocol sequence number
  var sentTransaction = 0;
  var testModeOut;
  var isConnected;
  var subscriptions;  // object trees we're mirroring from the server
  var received = [];  // queue of received messages (possibly out of sequence)
  var lastProcessed = $sync.util.now();     // last time a message was received
  var missedMessageTimeout = 500000;  // buffer out of order server messages for this many msec
		
  /** open a connection to the server */
  function init() {
    $sync.manager.registerConnection(self);
    subscriptions = $sync.set({
      $syncId:"#subscriptions"
    });
    if (!params || !params.testMode) {
      start();
    }
  }

  /** called when the sync connection to the server is opened
     * -- we've heard a response from the server in response to our initial connection attempt */
  function connected(data) {
    $debug.log("connected");
    isConnected = true;
    if (params && params.connected)	// notify anyone who's listening that we're now connected
      params.connected();
    receiveMessage(data);             // parse any data the server has waiting for us
  }

  /** @return true if we've an active sync connection to the server */
  self.isConnected = function() {
    return isConnected
  }
    
  /** start reading data from the network
     * TODO open and maintain a persistent connection to the server */
  function start() {
    $debug.log("starting connection to: " + feedUrl);
    var xact = startNextSendXact();
    xact.push({
      '#reconnect':false
    });
    send(xact, connected);
  }

  var syncFail = function(XMLHttpRequest, textStatus, errorThrown) {
    $debug.log("$sync protocol request failed: " + textStatus);
  };

  /** send message up to the server
     * (for now, we send each request over a separate http request) */
  var send = function(xact, successFn) {
    var xactStr = JSON.stringify(xact);
    $debug.log("sending xact to server: " + xactStr);
    $.ajax({
      url: feedUrl,
      type:"POST",
      dataType:"json",
      contentType:"application/json",
      success:successFn,
      error: syncFail,
      data: xactStr
    });
  };
    
  /** accept a feed for testing as if it came from the server */
  self.testFeed = function(jsonFeed) {
    receiveMessage(jsonFeed);
  };
    
  /** create the next transaction to send to the client */
  var startNextSendXact = function() {
    var xact = [];
    xact.push({
      '#transaction': sentTransaction++
    });
    return xact;
  };

  /** send all modified objects to the server */
  self.sendModified = function(changeSet) {
    var xact = startNextSendXact();
    xact.push({
      '#reconnect':true
    });

    changeSet.eachCheck(function(changed) {
      if (changed.changes.changeType === "create") {
        xact.push(changed.obj);
      }
    // TODO send modified objects too!
    });
    if (params && params.testMode)
      testModeOut = xact;
    else
      send(xact, receiveMessage);
  };

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
     * @param watchFunc - called when the subscribed root object arrives,
     *  (and fires again in the unlikely event the root is subsequently changed.)
     * @return a subscription object: {name: "string", root: rootObject}
     */
  self.subscribe = function(name, watchFunc) {
    var sub = $sync.subscription();
    sub.name = name;
    subscriptions.put(sub);
    if (watchFunc) {
      $sync.notification.watch(sub, function(subscription) {
        watchFunc(subscription.root)
      });
    }

    return sub;
  };

  /* accept a message from the network, process it if it's the next one
   * in the protocol transaction # sequence, othewise queue it until a
   * new message comes along.  */
  function receiveMessage(jsonMessage) {
    if (jsonMessage.length === 0)
      return;

    received.push(jsonMessage);
    processReceived();
  }

  /* process any messages that are ready */
  function processReceived() {
    var message;
    message = takeNextMessage();
    while (message) {
      parseMessage(message);
      message = takeNextMessage();
    }
  }
  
  /* take the next protocol message */
  function takeNextMessage() {
    var i, transactionNum, message;
    
    if (received.length === 0)
      return undefined;
    
    for (i = 0; i < received.length; i++) {
      message = received[i];
      transactionNum = findTransactionNum(message)
      if (transactionNum === receivedTransaction + 1) {
        receivedTransaction = transactionNum;
        received.splice(i, 1);
        lastProcessed = $sync.util.now();
        return message;
      }
    }
    if ($sync.util.now() - lastProcessed > missedMessageTimeout) {
      // TODO, reset the connection, etc.
      $debug.error("waited too long for message: " + (receivedTransaction + 1));
    } else {
      // LATER set a timer to reset the connection
      $debug.log("waiting for transaction: " + (receivedTransaction + 1));
    }
    return undefined;
  }
  
  /** find transaction # object in a message (normally it comes first, but scan just in case */
  function findTransactionNum(jsonMessage) {
    var number;

    number = $sync.util.arrayFind(jsonMessage, function(obj){
      var num = obj['#transaction'];

      if (num !== undefined)
        return num;
      return false;
    });

    if (number === undefined)
      $debug.error("missing #transaction in: " + JSON.stringify(jsonMessage));
    return number;
  }

  /** handle a feed of object changes from the server.
     * 1) instantiate new objects, enter them into the map
     * 2) update object references in received objects
     * 3) update properties of existing objects based on data received
     * 4) update collections
     * 5) send notifications to observers
     */
  function parseMessage(jsonFeed){
    var i, obj, toUpdate = [], toMetaSync = [], incomingTransaction;

    $debug.log("parseMessage: " + JSON.stringify(jsonFeed));
    if (jsonFeed.length === 0)
      return;


    // queue outgoing notifications until we're done processing
    $sync.notification.pause();
        
    // 1st pass through through the contents of the entire transaction
    for (i = 1; i < jsonFeed.length; i++) {
      obj = jsonFeed[i];
      if (obj == undefined) {
        $debug.warn("empty object in JSON stream");
      // be robust to empty elements in the protocol
      } else if (obj.hasOwnProperty("#edit")) {
        // handle collection changes a little later
        toMetaSync.push(obj);
      } else {
        // create and register objects heretofore unknown
        if (!$sync.manager.contains(obj.id)) {
          $sync.manager.createRaw(obj);
        }
        // update local objects with the received data (except meta _sync objects)
        toUpdate.push(obj);
      }
    }
		
    // process $ref references.  (all received objects should now be in the map)
    for (i = 0; i < jsonFeed.length; i++) {
      obj = jsonFeed[i];
      resolveRefs(obj);
    }
		
    // update objects by copying received data
    for (i = 0; i < toUpdate.length; i++) {
      obj = toUpdate[i];
      $sync.manager.update(obj);
    }
		
    // preform fancier updates e.g. collection membership changes
    for (i = 0; i < toMetaSync.length; i++) {
      obj = toMetaSync[i];
      $sync.manager.metasync(obj);
    }

    // transaction is complete:  tell everyone what we've done
    $sync.notification.endPause();
  }
	 
  /** replace any reference property value with the referenced object.
     * references look like this-  val:{$ref: 17}. which would be replaced
     * with the syncable object #17, e.g. val:{id:17, kind:"person", name:"fred"} */
  function resolveRefs(obj) {
    var prop, arrayDex, arrayVal;

    if (typeof obj !== 'object') {
      $debug.warn("resolveRefs can't resolve obj: " + obj);	// shouldn't happen
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
      return $sync.manager.get(obj.$ref);
    }
    return obj;
  }

  init();
    
  return self;
};

/** model object for subscriptions.  We send one of these to the server, and it will
 * create a subscription and send us the root object */
$sync.subscription = function(params) {
  var dataModel = {
    name:"unspecified",
    root:null
  };
  var self = $sync.manager.createSyncable("$sync.subscription", dataModel, params);
  return self;
}
