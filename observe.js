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
var $sync = $sync || {};


/* Manages callback functions for object and collection change notification.
 * Clients can register for change notification on objects or collections with
 * watch() and unregister with ignore.
 *
 * a global pause facility that queues all notifications is available, (it's
 * used to queue notifications until the end of a transaction.
 *
 * observer functions are passed the object being changed and a change
 * description object.  See below for examples.
 *
 * TODO rename this to observation.
 * TODO finish everyWatchers
 */
$sync.notification = function() {
    var that = {},
    deferred,
    watchers, // map of watching functions, indexed by the
    // id field of syncable objects, where each item
    // is an array of watch entries:
    // {func:callBack, owner:"callerSpecifiedToken"}
    collectionWatchers,  // same as watchers, but for collections
    everyWatchers, // array of watch entries for watch entries
    // that trigger on _all_ changes
    defer = false;

    that.reset = function() {
        deferred = [];
        watchers = {};
        everyWatchers = [];
        collectionWatchers = {};
    };

    /** watch for property changes on an object or an array of objects
     * fn(changedObj, changeDescription)  where changedObj is the object
     * that's been changed, and changeDescription is TODO */
    that.watch = function(objOrArray, fn, owner) {
        doWatch(watchers, objOrArray, fn, owner);
    };

    /** watch every registered syncable object in the system.  
     *
     * (This is used by the sync manager to identify changes to
     * every syncable and then propogate them to the server.) */
    that.watchEvery = function(fn, owner) {
        everyWatchers.push({
            call:fn,
            owner:owner
        });
    };

    /**
     * stop watching for changes
     */
    that.ignore = function(objOrArray, fnOrOwner) {
        doIgnore(watchers, objOrArray, fnOrOwner);
    };

    /** notify watchers that an object property has been changed */
    that.notify = function(obj, changes) {
        doNotify(watchers, obj, changes);
    };

    /** watch for membership/ordering changes on a syncable collection */
    that.collectionWatch = function(collection, fn, owner) {
        doWatch(collectionWatchers, collection, fn, owner);
    };

    /** stop watching for changes with a function previously registered via watch() */
    that.collectionIgnore = function(collection, fn, fnOrOwner) {
        doIgnoreChanges(collectionWatchers, collection, fnOrOwner);
    }

    /** notify watchers that collection membership or ordering has changed */
    that.collectionNotify = function(collection, changes) {
        doNotify(collectionWatchers, collection, changes);
    };


    /** defer all change notifications until endPause() is called */
    that.pause = function() {
        defer = true;
    };

    /** fire off all pending change notifications and deliver subsequent notifications immediately */
    that.endPause = function() {
        var deferDex;

        if (deferred.length) {
            for (deferDex = 0; deferDex < deferred.length; deferDex++) {
                deferred[deferDex]();
            }
        }
        defer = false;
    };


    that.debugPrint = function() {
        var str = "watchers: \n";

        function collectWatchers(watchDB) {
            var id, entryDex, entry, entries, func;

            for (id in watchDB) {
                entries = watchDB[id];
                for (entryDex = 0; entryDex < entries.length; entryDex++) {
                    entry = entries[entryDex];
                    func = entry.func ? "func()" : "?";
                    str += "\t#" + id + " - " + entry.owner + ":" + func + "\n";
                }
            }
        }
        collectWatchers(watchers);
        str += "collectionWatchers: \n";
        collectWatchers(collectionWatchers);

        str += "watchEvery: \n";
        everyWatchers.eachCheck(function(watcher) {
            str += "\t" + watcher.owner + ":" + watcher.call + "\n";
        });
        
        $debug.log(str);
    };


    var doIgnore = function(watchDB, objOrArray, fnOrOwner) {
        var ignoreOne = function(obj) {
            var watcherArray = watchDB[obj.id];
            if (watcherArray) {
                watchDB[obj.id] = watcherArray.filter (
                    function (entry) {
                        if (entry.func === fnOrOwner ||
                            entry.owner === fnOrOwner) {
                            return false;	// matched, so remove from array
                        }
                        return true;
                    }
                    );
            }
        };

        $sync.util.optionalArray(objOrArray, ignoreOne);
    };

    var doWatch = function(watchDB, objOrArray, fn, owner) {
        var watchOne = function(obj) {
            var watcherArray = watchDB[obj.id];
            if (!watcherArray) {
                watcherArray = watchDB[obj.id] = [];
            }
            watcherArray.push({
                func:fn,
                owner:owner
            });
        }

        $sync.util.optionalArray(objOrArray, watchOne);
    };

    var doNotify = function(watchDB, obj, changes) {
        var notify, notifyAll, callBacks = watchDB[obj.id];

        if (callBacks) {
            // notify all watchers registerd in the appropriate watchDB
            notifyAll = function() {
                callBacks.eachCheck( function(entry) {
                    entry.func(obj, changes); }); };

            if (defer) {
                deferred.push(notifyAll);
            } else {
                notifyAll(); } }

        if (everyWatchers) {
            everyWatchers.eachCheck(function(watcher) {
                notify = (function() {
                    watcher.call(obj, changes); });
                if (defer) {
                    deferred.push(notify);
                } else {
                    notify(); } }); } };

    that.reset();
    return that;
}();


/* Here are some examples of the change description object that's passed
 * to observation functions.
 *
 * from objA.fieldB_(10);
 *   desc = { changeType: "property",
 *            property: "fieldB",
 *            oldValue:null }
 *
 * from $sync.createSyncable(..)
 *   desc = { changeType: "create" }
 *
 * from set.put(newElem);
 *   desc = { changeType: "edit",
 *            put: newElem }
 *
 * from set.remove(newElem);
 *   desc = { changeType: "edit",
 *            remove: newElem }
 */

