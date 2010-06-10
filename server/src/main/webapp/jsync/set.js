
/*
 * Create a syncable set collection. Both the set and the elements themselves
 * are Syncable objects
 */
(function() {
  var log = $log.logger("$sync.set");

  $sync.set.instanceMethods ({ 
    _init: function() {
      this._size = 0;
      this._elems = {};  // hash of contained elements, indexed by id/partitions
    },
    
    /* add a syncable element to the syncable set */
    put: function(elem) {
      $debug.assert($sync.manager.isSyncable(elem), "set.put: elem isn't syncable: " + elem);
      if (!this.contains(elem)) {
        this._elems[$sync.manager.instanceKey(elem)] = elem; 
        this._size += 1;
        $sync.observation.notify(this, "edit", {
          put: elem
        });
      }
    },
    
    print: function() {
      var elem;
      for (elem in this._elems) {
        log.log(". " + this._elems[elem]);
      }
    },
    
    /**
     * put a syncable element in the set, (provided for compatibilty with
     * sequence.insert())
     * 
     * @param {Object}
     *            elem
     * @param {Object}
     *            after is ignored
     */
    insert: function(elem, after) {      
      this.put(elem);
    },
    
    /** remove a syncable element from the syncable set */
    remove: function(elem) {
      if (this.contains(elem)) {
        delete this._elems[$sync.manager.instanceKey(elem)];
        this._size -= 1;
        $sync.observation.notify(this, "edit", {
          remove: elem
        });
      }
    },
    
    contains: function(elem) {
      return this._elems.hasOwnProperty($sync.manager.instanceKey(elem));
    },
    
    size: function() {
      return this._size;
    },
    
    clear: function() {
      this._elems = [];
      this._size = 0;
      $sync.observation.notify(this, "edit", {
        clear: true
      });
    },
    
    /**
     * call func on each element in the set. iteration stops if the supplied
     * callback function returns a value
     */
    each: function(func) {
      var elem, result;
      for (elem in this._elems) {
        result = func(this._elems[elem]);
        if (result !== undefined) {
          return result;
        }
      }
      return null;
    }
  }); 
})();