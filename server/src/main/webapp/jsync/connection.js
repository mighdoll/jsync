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
 * @param params - optional options { testMode: true/false, connected: function }
 */
$sync.connect = function(feedUrl, params) {
  var self = {};
  var receivedTransaction = -1; 	// protocol sequence number received
  var sentTransaction = 0;		// protocol sequence number sent
  var testModeOut;                      // output for test mode
  var connectionToken = undefined;	// password for this connection 
  var subscriptions;                    // object trees we're mirroring from the server
  var received = [];                    // queue of received messages (possibly out of sequence)
  var lastProcessed = $sync.util.now(); // last time a message was received
  var missedMessageTimeout = 500000;    // buffer out of order server messages for this many msec
  var sendWhenConnected = [];	        // queue of messages to send once the logical connection is open
  var isClosed = false;

  /** open a connection to the server */
  function init() {
    // we keep the subscription list in sync with the server.  CONSIDER -- is this necessary?
    $sync.manager.withNewIdentity({
      partition: "subscriptions-unset",	// we change this after we connect.. TODO, fix this, it will break the manager's index
      id: "#subscriptions"
    }, function() {
      subscriptions = $sync.set();
    });
    if (params && params.testMode) {
      $sync.manager.registerConnection(self, "BrowserTestMode");
    }
    else {
      start(); // connect            
    }
  }
  
  /** close the connection.  Any subsequent messages received will be dropped.  Currently used only for testing.  */  
  self.close = function() {
  	isClosed = true;	
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
    var delta;						// jsonSync message fragment for a property change
    var edit;						// jsonSync message fragment for a collection change
    var target;						// target of the change
    var changeType;					// type of change
    var xact; 						// change we're sending
    var created;					// new object created

    if (changeSet.length === 0) {
      return;
    }
    
    xact = startNextSendXact();	    
    changeSet.eachCheck(function(change) {      
      target = change.target;
      changeType = change.changeType;
      if (changeType === "create") {
    	created = $.extend({kind:target.kind}, target);		// otherwise, stringify will skip kind, which is in the prototype
        xact.push(created);
      } else if (changeType === "property") {
        delta = {};
        delta.id = target.id;
        delta.$partition = target.$partition;
        delta[change.property] = target[change.property];
        xact.push(delta);
      } else if (changeType === "edit") {
        edit = {};
        edit['#edit'] = {id:target.id, "$partition":target.$partition};
        if (typeof(change.put) !== 'undefined') {
          edit.put = {id:change.put.id, "$partition":change.put.$partition};
        } else if (typeof(change.clear) !== 'undefined') {
          edit.clear = true;
        } else {
          $debug.error("sendModified doesn't know how to send change:" + change);     
        }
        
        xact.push(edit);  
      } else {
         $debug.error("sendModified doesn't know how to send change: " + change);     
      }
    });
    if (params && params.testMode)
      testModeOut = xact;
    else
      send(xact, receiveMessages);
  };

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
	$sync.manager.setDefaultPartition(connectionToken, function() {
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
  };
		
  /** start connection to the server */
  function start() {
    $debug.log("starting connection to: " + feedUrl);
    var xact = startNextSendXact();
    xact.push({'#start': true });
    sendNow(xact, connected);
  }
  
  /** called when the sync connection to the server is opened
   * -- we've heard a response from the server in response to our initial connection attempt */
  function connected(data) {
    $debug.log("connected");
    
    receiveMessages(data); // parse any data the server has waiting for us
    $debug.assert(connectionToken !== undefined);
    $sync.manager.registerConnection(self, connectionToken);
    
    subscriptions.$partition = connectionToken;	// scope subscription object to this connection before we send it up
    
    $sync.util.arrayFind(sendWhenConnected, function(queued) {
      sendNow(queued.xact, queued.successFn);
    });
    if (params && params.connected) // notify anyone who's listening that we're now connected
      params.connected(self);
    
//    if (!window.location.search.match(/[?&]longpoll=false/))
//      longPoll();
  }

  /** continually keep a request outstanding to the server */
  function longPoll() {
	send([], callAgain);
	  
	function callAgain() {
	  setTimeout(longPoll, 100);		
	};
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
    $debug.log("sending xact to server: " + xactStr);
    $.ajax({
      url: feedUrl,
      type: "POST",
      dataType: "json",
      contentType: "application/json",
      success: successFn,
      error: syncFail,
      data: xactStr
    });
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
	  $debug.log("connection (" + connectionToken + ") is closed.  Dropping these messages: " + messages);
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
    
    while (message = takeNextMessage()) {
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
      $debug.log("waiting for transaction: " + (receivedTransaction + 1));
    }
    return undefined;
  }
  
  /** find transaction # object in a message (normally it comes first, but scan just in case */
  function findTransactionNum(jsonMessage) {
    var number;

    number = $sync.util.arrayFind(jsonMessage, function(obj) {
      var num = obj['#transaction'];

      if (num !== undefined)
        return num;
      return false;
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
    
    $debug.log("parseMessage: " + JSON.stringify(message, null, 2));
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
      return $sync.manager.get(obj.$ref.$partition, obj.$ref.id);
    }
    return obj;
  }

  init();
    
  return self;
};

/** model object for subscriptions.  We send one of these to the server, and it will
 * create a subscription and send us the root object */
$sync.subscription = $sync.manager.defineKind('$sync.subscription', ['name', 'inPartition', 'root']);

/*
 * TODO support longpoll, should be pretty easy from now
 * LATER support websocket if available
 */
