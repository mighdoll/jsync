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

/* syncable singly linked list collection */
$sync.linkedList = function() {
    var that = {},
	
    init = function() {
        // these are public so that the current version of the $sync engine can get at them..
        that._size = 0;
        that.head = null;
    },
	
    size = function() {
        return that._size;
    },
	
    /** insert an element into the list.
     *
     * @param {Object} elem  item to insert
     * @param {Object} after (optional) insert item after this element or at the front if null
     */
    insert = function(elem, after) {
        // make element into a syncable link element
        elem.next || (elem.next = null);
        $sync.manager.makeSyncable(elem);  // fixme
        $debug.assert(!that.contains(elem));

        if (after) {
            elem.setNext(after.next);
            after.setNext(elem);
        } else {
            elem.setNext(that.head);
            that.head = elem;
        }
		
        that.set_size(that._size+1);
        $sync.notification.collectionNotify(this, {
            changeType:"edit",
            "insert":elem,
            "after":after
        });
    },
		
    remove = function(rem) {
        var prev;
        if (rem === null) {
            $debug.assert(false);
            return;
        }
		
        if (that.head === rem) {
            // remove head
            that.head = that.head.next;
            that.set_size(that._size-1);
        } else {
            // find element
            prev = that.head;
            while (prev && prev.next !== rem) {
                prev = prev.next;
            }
            if (prev && prev.next === rem) {
                // remove non-head element
                prev.setNext(rem.next);
                that.set_size(that._size-1);
            } else {
                $debug.log("not in list: " + jsDump.parse(rem));	// not in list
            }
        }
        $sync.notification.collectionNotify(this, {
            changeType:"edit",
            remove:rem
        });
    },
	
    contains = function(sought, matcher) {
        var result, elem = that.head;
		
        if (matcher) {
            for (result = false; elem && !result; elem = elem.next) {
                result = matcher(elem, sought);
            }
            return result;
        } else {
            while (elem && (elem !== sought)) {
                elem = elem.next;
            }
            if (elem === sought) {
                return elem;
            }
            return null;
        }
    },
	
    each = function(func) {
        var elem = that.head;
        while (elem && !func(elem)) {
            elem = elem.next;
        }
    },
	
    clear = function() {
        init();
    };
	
    init();
	
    that.each = each;
    that.size = size;
    that.remove = remove;
    that.contains = contains;
    that.insert = insert;
    that.clear = clear;
    return that;
}

/* element in the linked list */
$sync.linkElem = function() {
    var that;
		
    that = {
        next:null
    }
    return that;
}

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
            $sync.notification.collectionNotify(this, {
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
            $sync.notification.collectionNotify(this, {
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
