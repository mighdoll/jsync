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
var $sync = $sync || {}; // namespace

(function() {
  var log = $log.logger("$sync.sequence");
  
  /** --- private methods --- */
 
  function notifyInsertAt(elem, at) {
    $sync.observation.notify(this, "edit", {
      insertAt: {
        index: at,
        elem: elem
      }
    });
  }
  
  function findElem(sought) {
    var i;
    for (i = 0; i < this._elems.length; i++) {
      if (this._elems[i] === sought) 
        return i;
    }
    return undefined;
  }
  
  function doAppend(item) {
    $debug.assert($sync.manager.isSyncable(item), "sequence.put: elem isn't syncable: " + item);
        
    if (this.contains(item)) 
      return;
    this._elems.push(item);
    
    notifyInsertAt.apply(this, [item, this._elems.length - 1]);
  }
  
  /** --- public methods --- */
  
  $sync.sequence.instanceMethods({
    _init: function() {
      this._elems = []; // array of set elements
    },
    
    append: doAppend,
    
    /**
     * CONSIDER can we rename this to append, or do we need compatibility with
     * set.put()
     */
    put: doAppend,
    
    getAt: function(index) {
      return this._elems[index];
    },
    
    size: function() {
      return this._elems.length;
    },
    
    clear: function() {
      this._elems = [];
      $sync.observation.notify(this, "edit", {
        clear: true
      });
    },
    
    contains: function(item) {
      return this.indexOf(item) !== undefined;
    },
    
    remove: function(item) {
      this.removeAt(this.indexOf(item));
    },
    
    removeAt: function(index) {
      if (index !== undefined) {
        this._elems.splice(index, 1);
        $sync.observation.notify(this, "edit", {
          removeAt: index
        });
      }
    },
    
    print: function() {
      var i, elem;
      for (i = 0; i < this._elems.length; i++) {
        elem = this._elems[i];
        log.log(i + " -> " + elem);
      }
    },
    
    /**
     * Insert `item` after `prev`. If `prev` is falsey, insert `item` at the
     * beginning of the list.
     */
    insert: function(item, prev) {
      $debug.assert($sync.manager.isSyncable(item), "sequence.insert: elem isn't syncable: " + item);
      $debug.assert(!prev || this.contains(prev));
      
      var index = 0;
      var prevDex = prev && this.indexOf(prev);
      if (prevDex !== undefined) {
        index = prevDex + 1;
      }
      
      this._elems.splice(index, 0, item);
      notifyInsertAt.apply(this, [item, index]);
    },
    
    /**
     * Insert item into the array at the specified index, moving later elements
     * down to make room
     * 
     * @param {Object}
     *            item
     * @param {Object}
     *            index
     */
    insertAt: function(item, index) {
      $debug.assert($sync.manager.isSyncable(item), "sequence.insertAt: elem isn't syncable: " + item);
      
      this._elems.splice(index, 0, item);
      notifyInsertAt.apply(this, [item, index]);
    },
    
    /**
     * Move `item` to the specified index position. If toDex is undefined, move
     * item to the first position.
     */
    move: function(item, toDex) {
      $debug.assert(this.contains(item));
      this.moveAt(this.indexOf(item), toDex);
    },
    
    /**
     * Move item from the specified index position to a new position. If toDex
     * is undefined, move item to the first position.
     */
    moveAt: function(fromDex, toDex) {
      var elem = this._elems[fromDex];
      this._elems.splice(fromDex, 1);
      if (toDex === undefined) {
        toDex = 0;
      }
      this._elems.splice(toDex, 0, elem);
      $sync.observation.notify(this, "edit", {
        move: {
          from: fromDex,
          to: toDex
        }
      });
    },
    
    /**
     * call a function for each element. If the function returns a value, stop
     * iteration and return the value.
     */
    each: function(func) {
      var i, result;
      for (i = 0; i < this._elems.length; i++) {
        result = func(this._elems[i], i);
        if (result !== undefined) {
          return result;
        }
      }
      return undefined;
    },
    
    indexOf: findElem  
  });
  
})();

