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
var $sync = $sync || {}; // namespace

$sync.sequence = function() {
  var self = $sync.manager.createSyncable("$sync.sequence");
  var elems = []; // array of set elements

  self.put = function(item) {
    $debug.assert($sync.manager.isSyncable(item), "sequence.put: elem isn't syncable: " + item);
    if (self.contains(item)) 
      return;
    var sortElem = {
      sortKey: newMaxKey(),
      elem: item
    };
    elems.push(sortElem);
    
    notifyInsert(sortElem);
  };
  
  self.size = function() {
    return elems.length;
  };
  
  self.clear = function() {
    elems = [];
    $sync.observation.notify(self, "edit", {
      clear:true
    });
  };
  
  self.contains = function(item) {
    return findElem(item) !== undefined;
  };
  
  self.remove = function(item) {
    var dex = findElem(item);
    if (dex !== undefined) {
      elems.splice(dex, 1);
      $sync.observation.notify(self, "edit", {
        remove: item
      });
    }
  };
  
  self.print = function() {
    var i, elem;
    for (i = 0; i < elems.length; i++) {
      elem = elems[i];
      $debug.log(elem.sortKey + " -> " + elem);
    }
  };
  
  /** Insert `item` after `prev`.  If `prev` is falsey, insert `item` at the
      beginning of the list. */
  self.insert = function(item, prev) {
    $debug.assert($sync.manager.isSyncable(item), "sequence.insert: elem isn't syncable: " + item);
    $debug.assert(!self.contains(item));
    $debug.assert(!prev || self.contains(prev));
    var prevDex = prev && findElem(prev);
    var key;
    if (prev) {
      key = keyAfter(prevDex);
    } else {
      prevDex = -1;
      key = newMaxKey();
    }
    var sortElem = {
      sortKey: key,
      elem: item
    };
    elems.splice(prevDex + 1, 0, sortElem);
    notifyInsert(sortElem);
  };
  
  /** Move `item` after `prev`.  If `prev` is falsey, move `item` to the
      beginning of the list. */
  self.move = function(item, prev) {
    $debug.assert(self.contains(item));
    $sync.observation.withoutNotifications(function() {
        self.remove(item);
        self.insert(item, prev);
      });
    $sync.observation.notify(self, "edit", {
      move: item,
      after: prev
    });
  }

  self.each = function(func) {
    var i, result;
    for (i = 0; i < elems.length; i++) {
      result = func(elems[i].elem, i);
      if (result !== undefined) {
        return result;
      }
    }
    return undefined;
  };
  
  self.indexOf = findElem;
  
  function notifyInsert(sortElem) {
    $sync.observation.notify(self, "edit", {
      insertAt: {
        key: sortElem.sortKey,
        elem: sortElem.elem
      }
    });
  }
  
  function keyAfter(prevDex) {
    var prevKey, nextKey, key;
    if (prevDex === elems.length - 1) {
      return prevDex + 100;
    }
    prevKey = elems[prevDex].sortKey;
    nextKey = elems[prevDex + 1].sortKey;
    
    key = (prevKey + nextKey) / 2;
    
    // eventually we need to recreate keys
    $debug.assert(key != prevKey);
    $debug.assert(key != nextKey);
    
    return key;
  }
  
  function newMaxKey() {
    if (!elems.length) {
      return 100;
    }
    else {
      return elems[elems.length - 1].sortKey + 100;
    }
  }
  
  function findElem(sought) {
    var i;
    for (i = 0; i < elems.length; i++) {
      if (elems[i].elem === sought) 
        return i;
    }
    return undefined;
  }
  
  return self;
};

/* Create a syncable set collection.  Both the set and the elements
 themselves are Syncable objects */
$sync.set = function() {
  var self = $sync.manager.createSyncable("$sync.set");
  var size = 0; // number of elements
  var elems = {}; // associative array of set elements
  
  /* add a syncable element to the syncable set */
  self.put = function(elem) {
    $debug.assert($sync.manager.isSyncable(elem), "set.put: elem isn't syncable: " + elem);
    if (!self.contains(elem)) {
      elems[elem.id] = elem;
      size += 1;
      $sync.observation.notify(self, "edit", {
        put: elem
      });
    }
  };

  /** put a syncable element in the set,
   * (provided for compatibilty with sequence.insert())
   * 
   * @param {Object} elem  
   * @param {Object} after is ignored
   */    
  self.insert = function(elem, after) {
    self.put(elem);
  }
    
  /** remove a syncable element from the syncable set */
  self.remove = function(elem) {
    if (self.contains(elem)) {
      delete elems[elem.id];
      size -= 1;
      $sync.observation.notify(self, "edit", {
        remove: elem
      });
    }
  };
  
  self.contains = function(elem) {
    return elems.hasOwnProperty(elem.id);
  };
  
  self.size = function() {
    return size;
  };
  
  self.clear = function() {
    elems = [];
    size = 0;
    $sync.observation.notify(self, "edit", {
      clear:true
    });
  };
  
  /** call func on each element in the set.  iteration stops if the supplied
   * callback function returns a value */
  self.each = function(func) {
    var elem, result;
    for (elem in elems) {
      result = func(elems[elem]);
      if (result !== undefined) {
        return result;
      }
    }
    return null;
  };
  
  
  return self;
}

$sync.manager.defineKind("$sync.set", null, $sync.set);
$sync.manager.defineKind("$sync.sequence", null, $sync.sequence);
