/**
 * @author lee
 */

var $sync = $sync || {};      // namespace

$sync.receive = function(connection) {
  var log = $log.logger("receive");
  var received = [];                    // queue of received messages (possibly out of sequence)
  var receivedTransaction = -1;         // protocol sequence number received
  var lastProcessed = $sync.util.now(); // last time a message was received
  var missedMessageTimeout = 500000;    // buffer out of order server messages for this many msec

  var self = {
    /** accept an array of messages from the network, process immediately 
     * if we have next message in the protocol transaction # sequence, othewise 
     * queue it until new message comes along.  */
    receiveMessages: function(messages) {
      var msgDex;
      log.detail("receiveMessages: ", messages);
      if (!messages || messages.length === 0) 
        return;
      
      if (connection.isClosed) {
        if (!connection.isClosed.ignoreMessages) {
          log.warn("connection (" + connection.connectionToken +
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
  };
  
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
    
    if (received.length === 0) {
//      log.debug("empty message received");
      return undefined;
    }
    
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
      log.error("waited too long for message: " + (receivedTransaction + 1));
    } else {
      // LATER set a timer to reset the connection in case it's never received
      log.debug("connection.takeNextMessage() waiting for out of order transaction: " + (receivedTransaction + 1));
    }
    return undefined;
  }
  
  /** find transaction # object in a message (normally it comes first, but scan just in case */
  function findTransactionNum(jsonMessage) {
    var number;

    number = $sync.util.arrayFind(jsonMessage, function(obj) {
      return obj['#transaction'];
    });

    if (number === undefined)
      log.error("missing #transaction in: " + JSON.stringify(jsonMessage));
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
    
    log.detail("parseMessage: ", message);
    if (message.length === 0) 
      return;
    
    // queue outgoing notifications until we're done processing
    $sync.observation.pause();
    $sync.observation.serverMutator();
    
    // 1st pass through through the contents of the entire transaction
    for (i = 0; i < message.length; i++) {
      obj = message[i];
      if (obj === undefined) {
        // empty elements oughtn't be in the stream 
        log.warn("empty object in JSON stream");
      }
      else if (obj.hasOwnProperty("#edit")) {
        // queue collection changes to handle a little later
        toEdit.push(obj);
      }
      else if (obj.hasOwnProperty("#reset")) {
        log.error("#reset not yet implemented");
      }
      else if (obj.hasOwnProperty("#transaction")) {
      }
      else if (obj.hasOwnProperty("#token")) {
        $debug.assert(connection.connectionToken === undefined);
        connection.connectionToken = obj["#token"];
      }
      else if (hasHashProperty(obj)) {
        log.error("unsupported #property in: " + JSON.stringify(obj));
      }
      else {
        // create and register objects heretofore unknown
        if (!$sync.manager.contains(obj.$partition, obj.$id)) {
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
      $sync.update.updateInstance(obj);
    }
    
    // preform fancier updates e.g. collection membership changes
    for (i = 0; i < toEdit.length; i++) {
      obj = toEdit[i];
      $sync.update.updateCollection(obj);
    }
    
    // transaction is complete:  tell everyone what we've done
    $sync.observation.popMutator();
    $sync.observation.endPause();
  }

  /** @return true if an object contains a property starting with '#'.
   * properties starting with '#' are resevered for jsync control messages
   */   
  function hasHashProperty(obj) {
    for (var prop in obj) {
      if (prop[0] === '#')
        return true;
    }
    return false;
  }
     
  /** replace any reference property value with the referenced object.
     * references look like this-  val:{$ref: 17}. which would be replaced
     * with the syncable object #17, e.g. val:{$id:17, $kind:"person", name:"fred"} */
  function resolveRefs(obj) {
    var prop, arrayDex, arrayVal;

    if (typeof obj !== 'object') {
      log.warn("resolveRefs can't resolve obj: " + obj); // shouldn't happen
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
      return $sync.manager.get(obj.$ref.$partition, obj.$ref.$id);
    }
    return obj;
  }
  
  
  return self;
};  