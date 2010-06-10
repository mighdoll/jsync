/*
 * Copyright [2009] Digiting Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
var $sync = $sync || {};

/*
 * Manages callback functions for object and collection change notification.
 * Clients can register for change notification on objects or collections with
 * watch() and unregister with ignore.
 * 
 * a global pause facility that queues all notifications is available, (it's
 * used to queue notifications until the end of a transaction.
 * 
 * observer functions are passed the object being changed and a change
 * description object. See below for examples.
 */
$sync.observation = function() {
  var log = $log.logger("$sync.observation");
  var deferred;
  var watchers; // map of watching functions, indexed by the
  // $id field of syncable objects, where each item
  // is an array of watch entries:
  // {func:callBack, owner:"callerSpecifiedToken"}
  var everyWatchers; // array of watch entries
  // that trigger on _all_ changes
  var defer = false;
  var enabled = true;
  var mutators;
  var self = {

    /** reset back to initial state */
    reset : function() {
      deferred = [];
      watchers = {};
      everyWatchers = [];
      mutators = [ "local" ];
    },

    /** return the current mutator, (currently either "server" or "local" */
    currentMutator : function() {
      return mutators[mutators.length - 1];
    },

    /**
     * all mutuations until the next pop() will be attributed to the server SOON
     * get rid of this, just use withMutator()
     */
    serverMutator : function() {
      mutators.push("server");
    },

    /**
     * return to previous mutator. currently we never get more than 1 deep
     * anyway SOON get rid of this, just use withMutator()
     */
    popMutator : function() {
      if (mutators.length <= 1) {
        $debug.fail("popMutator() too many times");
      } else {
        mutators.pop();
      }
    },

    /**
     * call a function, all changes will be marked as originating from the
     * mutator
     */
    withMutator : function(mutator, fn) {
      mutators.push(mutator);
      fn();
      mutators.pop();
    },

    /**
     * watch for property changes on an object or an array of objects
     * fn(changedObj, changeDescription) where changedObj is the object that's
     * been changed. See changeDescription to see a description of the changes.
     */
    watch : function(objOrArray, fn, owner) {
      $sync.util.each(objOrArray, function(target) {
        watchOne(target, fn, owner);
      });
    },

    /**
     * watch for a particular property to change on an object or an array of
     * objects
     */
    watchProperty : function(objOrArray, property, fn, owner) {

      /** called when the object is changed */
      function changed(change) {
        if (change.changeType === "property" && change.property === property) {
          fn(change);
        }
      }

      self.watch(objOrArray, changed, owner);
    },

    /**
     * watch every registered syncable object in the system.
     * 
     * (This is used by the sync manager to identify changes to every syncable
     * and then propogate them to the server. CONSIDER this may send unnecessary
     * changes to the server. e.g. if an object is modified and then discarded)
     */
    watchEvery : function(fn, owner) {
      everyWatchers.push( {
        call : fn,
        owner : owner
      });
    },

    /**
     * stop watching for changes
     */
    ignore : function(objOrArray, fnOrOwner) {
      $sync.util.each(objOrArray, function(target) {
        ignoreOne(target, fnOrOwner);
      });
    },

    /** notify watchers that an object or collection has been changed */
    notify : function(target, changeType, changeParams) {
      // log.debug("notify(): ", target, changeType, changeParams);
    if (!enabled)
      return;
    var notify, notifyAll;
    var callBacks = watchers[target.$id];
    var change = $sync.changeDescription(changeType, target, changeParams);
    var propChangeFns;

    if (callBacks) {
      // notify all watchers registered in the appropriate watchDB
    notifyAll = function() {
      $.each(callBacks, function(index, entry) {
        // $log.debug("notify: " + target + " changes:" + change);
          entry.func(change);
        });
    };

    if (defer) {
      deferred.push(notifyAll);
    } else {
      notifyAll();
    }
  }

  if (everyWatchers) {
    $.each(everyWatchers, function(index, watcher) {
      notify = (function() {
        watcher.call(change);
      });
      if (defer) {
        deferred.push(notify);
      } else {
        notify();
      }
    });
  }

  if (change.changeType === 'property') {
    propChangeFns = target["$" + change.property + "ChangeFns"];
    if (propChangeFns) {
      $.each(propChangeFns, function() {
        this(change);
      });
    }
  }
},

/** defer all change notifications until endPause() is called */
pause : function() {
  defer = true;
},

/**
 * fire off all pending change notifications and deliver subsequent
 * notifications immediately
 */
endPause : function() {
  var deferDex;
  var toCall = deferred.concat();

  defer = false;
  deferred = [];

  if (toCall.length) {
    for (deferDex = 0; deferDex < toCall.length; deferDex++) {
      toCall[deferDex]();
    }
  }
},

/** execute fn() without sending any notifications */
withoutNotifications : function(fn) {
  var savedEnabled = enabled;
  try {
    enabled = false;
    return fn();
  } finally {
    enabled = savedEnabled;
  }
},

debugPrint : function() {
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
  $.each(everyWatchers, function(index, watcher) {
    str += "\t" + watcher.owner + ":" + watcher.call + "\n";
  });

  $log.log(str);
}
  };

  function watchOne(obj, fn, owner) {
    // log.debug("watchOne: " + obj + " watcher: " + owner);

    var watcherArray = watchers[obj.$id];
    if (!watcherArray) {
      watcherArray = watchers[obj.$id] = [];
    }
    watcherArray.push( {
      func : fn,
      owner : owner
    });
  }

  function ignoreOne(obj, fnOrOwner) {
    var watcherArray = watchers[obj.$id];
    if (watcherArray) {
      watchers[obj.$id] = watcherArray.filter(function(entry) {
        if (entry.func === fnOrOwner || entry.owner === fnOrOwner) {
          return false; // matched, so remove from array
        }
        return true;
      });
    }
  }

  self.reset();
  return self;
}();
