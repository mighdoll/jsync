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

/* maintains a network connection to a server feed containing the jsonsync protocol.
 * @param jsonSyncFeed - url of the jsonsync protocol endpoint
 * @param params - optional options { testMode: true/false
 */
$sync.connect = function(jsonSyncFeed, params) {
    var that = {},
        receivedTransaction = 0, // protocol sequence number
        sentTransaction = 0,
        testModeOut,
        subscriptions;  // object trees we're mirroring from the server
		
    var init = function() {
      $sync.manager.registerConnection(that);
      subscriptions = $sync.set({$syncId:"#subscriptions"});
      if (!params || !params.testMode) {
        start(jsonSyncFeed); } };

    /* start reading data from the network
     * TODO open and maintain a persistent connection to the server */
    var start = function(jsonFeed) {
        $.ajax({
            url: jsonFeed,
            type:"POST",
            dataType:"json",
            success:parseFeed,
            error:syncFail,
            data: {sessionId: "sessionId"} });  };

    var syncFail = function(XMLHttpRequest, textStatus, errorThrown) {
        $debug.log("$sync protocol request failed: " + errorThrown); };

    /** accept a feed for testing as if it came from the server */
    that.testFeed = function(jsonFeed) {
        parseFeed(jsonFeed);
    };

    /** send all modified objects to the server */
    that.sendModified = function(changeSet) {
        var xact = [], changed;

        xact.push({transaction: sentTransaction++});
        changeSet.eachCheck(function(changed) {
            if (changed.changes.changeType === "create") {
                xact.push(changed.obj);
            }
        });
        if (params && params.testMode) {
            testModeOut = xact;
        }
    };

    that.testOutput = function() {
        return testModeOut;
    };
    
    /* Subscribe to server propogated changes on a set of objects
     * The caller can watch for changes on the returned subscription object.
     * When the root field is filled in, the subscription request has been
     * filled.
     *
     * @param name - well known name shared by the server to identify this
     *  tree of subscribed objects
     * @param watchFunc - called when the subscribed root object arrives,
     *  and fires again in the unlikely event the root is subsequently changed.
     * @return a subscription object: {name: "string", root: rootObject}
     */
    that.subscribe = function(name, watchFunc) {
        var sub = $sync.subscription();
        sub.name = name;
        subscriptions.put(sub);
        if (watchFunc) {
            $sync.notification.watch(sub, function(subscription) {
                watchFunc(subscription.root) }); }

        return sub; };

    /* handle a feed of object changes from the server.
     * 1) instantiate new objects, enter them into the map
     * 2) update object references in received objects
     * 3) update properties of existing objects based on data received
     * 4) update collections
     * 5) send notifications to observers
     */
    var parseFeed = function(jsonFeed){
        var i, obj, toUpdate = [], toMetaSync = [], incomingTransaction;
		
        $debug.assert(jsonFeed.length >= 2);
        incomingTransaction = jsonFeed[0].transaction;
        if (incomingTransaction === receivedTransaction + 1) {
            receivedTransaction += 1;
        }
        else {
            // TODO advise the server that we missed a transaction, and reissue.
            $debug.assert(false, "parseFeed: missed a transaction: " + receivedTransaction);
            return;
        }

        // defer outgoing modifications
        $sync.notification.pause();
        
        // 1st pass through through the contents of the entire transaction
        for (i = 1; i < jsonFeed.length; i++) {
            obj = jsonFeed[i];
            if (obj.hasOwnProperty("#edit")) {
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
    };
	 
    /*
     * replace any reference property value with the referenced object.
     * references look like this-  val:{$ref: 17}. which would be replaced
     * with the syncable object #17, e.g. val:{id:17, kind:"person", name:"fred"}
     */
    var resolveRefs = function(obj) {
        var prop, arrayDex, arrayVal;

        if (typeof obj !== 'object') {
            $debug.assert(false);	// shouldn't happen
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
        }
        else {
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
    };

    init();
    
    return that;
};

$sync.subscription = function(params) {
    var dataModel = {name:"unspecified", root:null};
    var that = $sync.manager.createSyncable("$sync.subscription", dataModel, params);
    return that;
}

