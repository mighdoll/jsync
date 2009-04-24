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

$sync.sortedSet = function(params) {
    var that = $sync.manager.createSyncable("$sync.sortedSet", null, params),
    elems = []; // array of set elements

    that.put = function(item) {
        $debug.assert($sync.manager.isSyncable(item), "sortedSet.put: elem isn't syncable: "+ item);
        var sortElem = {sortKey:newMaxKey(), elem:item};
        elems.push(sortElem);

        notifyInsert(sortElem);
    };

    that.size = function() {
        return elems.length;
    };

    that.clear = function() {
        elems = [];
    };

    that.insert = function(item, prev) {
        $debug.assert($sync.manager.isSyncable(item), "sortedSet.insert: elem isn't syncable: "+ item);
        var prevDex = prev && findElem(prev);
        if (prevDex) {
            var key = keyAfter(prevDex);
            var sortElem = {sortKey:key, elem:item};
            elems.splice(prevDex, 0, sortElem);
            notifyInsert(sortElem);
        } else {
            that.put(item);
        }
    };

    that.each = function(func) {
        var i, result;
        for (i = 0; i < elems.length; i++) {
            result = func(elems[i].elem);
            if (result) {
                return result;
            }
        }
        return null;
    };


    var notifyInsert = function(sortElem) {
        // TODO -- needs to notify with the new index too!
        $sync.notification.collectionNotify(that, {
            changeType: "edit",
            put: sortElem.elem
        });
    }

    var keyAfter = function(prevDex) {
        var prevKey, nextKey, key;
        if (prevDex === elems.length - 1) {
            return prevDex + 100;
        }
        prevKey = elems[prevDex].sortKey;
        nextKey = elems[prevDex+1].sortKey;

        key = (prevKey + nextKey) / 2;

        // eventually we need to recreate keys
        $debug.assert(key != prevKey);
        $debug.assert(key != nextKey);
        
        return key;
    }

    var newMaxKey = function() {
      if (!elems.length){
          return 100;
      } else {
          return elems[elems.length - 1].sortKey + 100;
      }
    }

    var findElem = function(find) {
        var i;
        for (i = 0; i < elems.length; i++) {
            if (elems[i] === find)
                return i;
        }
        return -1;
    }

    return that;
};

/* Syncable set collection, both the set and the elements are Syncable objects */
$sync.set = function(params) {
    var that = $sync.manager.createSyncable("$sync.set", null, params),
    size = 0,   // number of elements
    elems = {}; // associative array of set elements

    var init = function() { };
    
    /* add a syncable element to the syncable set */
    that.put = function(elem) {
        $debug.assert($sync.manager.isSyncable(elem), "set.put: elem isn't syncable: "+ elem);
        if (!that.contains(elem)) {
            elems[elem.id] = elem;
            size += 1;
            $sync.notification.collectionNotify(that, {
                changeType: "edit",
                put: elem
            });
        }
    };


    /* remove a syncable element from the syncable set */
    that.remove = function(elem) {
        if (that.contains(elem)) {
            delete elems[elem.id];
            size -= 1;
            $sync.notification.collectionNotify(that, {
                changeType: "edit",
                remove: elem
            });
        }
    };

    that.contains = function(elem) {
        return elems.hasOwnProperty(elem.id);
    };

    that.size = function() {
        return size;
    };

    /* call func on each element in the set.  iteration stops if the supplied
     * callback function returns a value */
    that.each = function(func) {
        var elem, result;
        for (elem in elems) {
            result = func(elems[elem]);
            if (result) {
                return result;
            }
        }
        return null;
    };

    init();
	
    return that;
}
