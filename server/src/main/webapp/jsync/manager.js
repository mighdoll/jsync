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

/*
 * All syncable javascript objects contain an 'id' property and a 'kind' property.
 * id+kind is guaranteed to be a unique, and is currently implemented as a string.
 * 
 * kind is akin to the class of the object - the client typically uses the kind to
 * attach functions to the data object arriving over the wire. 
 *
 * We maintain a mapping table that maps unique ids to javascript objects.  Objects
 * are entered into the table when they are first received from the network.  LATER
 * note that currently the table is not garbage collected, so syncable objects
 * are never freed.
 * 
 * Each syncable object instance has two prototypes in its prototype chain.  The immediate
 * prototype, called the kindPrototype, contains property setter functions of the 
 * form:  function property_(value).  The setter functions perform notification as
 * they change the object state.  The kindPrototype has a prototype too called the 
 * kindBasePrototype, which adds some debug logging for syncables. 
 */
var $sync = $sync || {};

/**
 * - manages a local pool of syncable objects.
 * - provides functions for creating syncable kinds(aka types or classes) and syncable instances (CONSIDER moving these to a separate class)
 * - supports updating syncable instances to reflect server generated changes
 */
$sync.manager = function() {
  var self = {};
  var ids;              // mapping of partition,ids to objects
  var connection;       // for now, the browser has only one connection
  var changeSet;        // uncommitted changes to be sent to the server on commit()
  var nextId;           // next id to use for locally created objects
  var clientId = "Uninitialized-"; // id prefix for locally created objects LATER protocol should give a unique id for this session
  var kindPrototypes = {};  // map of prototype objects for each kind, indexed by kind string
  var constructors = {};// map of instance constructors, indexed by kind.
  var defaultPartition; // create object in this partition by default
  var nextIdentity;		// set the next instance ids to the contained {partition:, id:} pair
  var autoCommit;		// automatically commit changes to the server if this is set
	
  /** reset to initial state (useful for testing!) */
  self.reset = function() {
    ids = {};
	defaultPartition = "partition-unset";
    connection = null;
    changeSet = [];
    nextId = 0;
	nextIdentity = undefined;
    $sync.observation.reset();
    $sync.observation.watchEvery(syncableChanged, "$sync.manager");
  };
  
  self.autoCommit = function(auto) {
    if (auto === undefined) {
      autoCommit = true;
    }
    else {
      autoCommit = auto;
    }
  }

  /** @return true iff obj has the requisite sync fields (kind and id) and
   * registered with the sync manager */
  self.isSyncable = function(obj) {
    if (!obj.id || !obj.kind) {
      return false;
    }
    return self.get(obj.$partition, obj.id) === obj;
  };
  
  /** 
   * Set the identity (partition, id) for the next created object 
   * 
   * used when instantiating remote objects
   *  
   * @param {Object} ids
   * @param {Object} fn
   */
  self.withNewIdentity = function(ids, fn) {
  	var oldIds = nextIdentity;
	nextIdentity = ids;
	fn();
	nextIdentity = oldIds;
  };
  
  /**
   * Returns an object that represents a $sync kind.  This object is a
   * construction function (not a constructor) that takes a hash of
   * properties, and returns an object of kind `kind` that has been
   * initialized with these properties.  Attributes in `dataModel` (a
   * hash, or array of property names) are part of the syncable data
   * model; other attributes are stored in the object as properties, but
   * not synced.
   *
   * @param {Object} kind
   * @param {Object} dataModel
   * @param {Object} providedConstructor
   * 
   * @return constructor function for objects of this kind 
   */
  self.defineKind = function(kind, dataModel, providedConstructor) {
//  	$debug.log("defineKind: " + kind);
	if (kindPrototypes[kind] !== undefined) {
	  $debug.assert("defineKind called twice on kind: " + kind);
	  return;
	}
    if (dataModel instanceof Array) { 	  // convert array of property names to hash
      var model = {};
      $.each(dataModel, function(_, name) {
        model[name] = null; 
      });
      dataModel = model;	
    } 
	
	makeKindPrototype(kind, dataModel);
	constructors[kind] = providedConstructor || constructor;	
    return providedConstructor ? null : constructor;
	
	/**
	 * Constructor for this kind of object
	 * 
	 * @param {Object} instanceData - hash of {property:value} initial values
	 * for the new instance
	 */
    function constructor(instanceData) {
      var object = $sync.manager.createSyncable(kind, instanceData);
      $.extend(object, instanceData);
      return object;
    }
  };
	
  /** Instantiate a new syncable object.  This creates a prototype, and
   * instantiates it.  A subsequent call with the same kind or dataModel.kind
   * will use the same prototype.
   * 
   * @param kind : ?string - specifies the syncable kind for the syncable object
   *    kind typically refers to a constructor function, which will be called
   *    when objects of this kind are received from the network.  defaults to
   *    dataModel.kind.
   * @param instanceData - optional instance data copied to this instance.  
   */
  self.createSyncable = function(kind, instanceData) {
    var kindProto, obj;
    
    // get kind for new object
    if (!kind) { // use the provided kind	(SOON get rid of !kind cases, -lee)
      $debug.fail("specify a kind in createSyncable()");	  
    }
    
    // create a javascript object instance with appropriate prototype for this kind
    kindProto = kindPrototype(kind);
	obj = $sync.util.createObject(kindProto);
    
	// setup identity for this object
	if (nextIdentity) {
	  obj.$partition = nextIdentity.partition;
	  obj.id = nextIdentity.id;
	} else {
      obj.$partition = defaultPartition;
	  obj.id = clientId + nextId++;	
	}

	// populate with instance data    
	$.extend(obj, instanceData);
	
    // install in map and let people know.  we've made a new syncable!
    self.put(obj);
    $sync.observation.notify(obj, "create");
    return obj;
  };

  /** send all modified objects to the server */
  self.commit = function() {
    connection.sendModified(changeSet);
    changeSet = [];
  };
  
  /** register an existing sync channel */
  self.registerConnection = function(conn, id) {
    connection = conn;
    clientId = "*" + id;
  };

  /** log the entire local syncable instance table for debugging */  
  self.printLocal = function() {
    $debug.log("local syncable instances:");
    for (id in ids) {
      if (typeof id !== 'function' && ids.hasOwnProperty(id)) {
         $debug.log("  " + ids[id]);
      }
    }
  };

  /** update a syncable object by copying fieldwise from a received data object.
   * the target object is identified by the id of the received object
   *
   * assumes that there is syncable object with the same id as received.
   */
  self.update = function(received) {
    var prop, obj = self.get(received.$partition, received.id);
    
    $debug.assert(obj && obj !== received);
    $debug.assert(obj.id === received.id);
    $debug.assert(!received.kind || obj.kind === received.kind);
        
    // update the target object with the received data
    for (prop in received) {
      // expecting only data fields in the received object
      $debug.assert(typeof received[prop] !== 'function');
      
      // call setter to set to received value. This notifies client observers
      // of the change.  
      if (!reservedProperty(prop)) {
        // these changes came from the server, no sense sending them back
        $debug.assert($sync.observation.currentMutator() === "server");
        obj[prop + "_"](received[prop]);
      }
    }
  };
	
  /** process a "#edit" object in the sync feed.
   * #edit objects contain:
   * here's an example edit object:
   *   { "#edit" : {id:1, $partition:"test"},         // target object id
   *   						// put objects 2,3 into object 1
   *     "put": [{id:2, $partition:"test"}, {id:3, $partition:"test"}]			 
   * 
   * CONSIDER--move this protocol related stuff to connection.js ?
   */
  self.collectionEdit = function(edit) {
  	var editRef = edit["#edit"];
    var collection = self.get(editRef.$partition, editRef.id);
    if (typeof(collection) === 'undefined') {
      $debug.error("target of collection edit not found: " + JSON.stringify(edit));
	  $sync.manager.printLocal();
	  debugger;
    } else if (collection.kind === "$sync.set") {
      editSet(collection, edit);
    } else {
      $debug.log("unexpected kind of collection for #edit: " + JSON.stringify(edit) + " found: " + JSON.stringify(collection));
    }
  };
    
  /** add obj to the map unless the map already has an object at that (id,$partition)
   *
   * @param obj object to add
   */
  self.put = function(obj) {
  	var key = self.instanceKey(obj.$partition, obj.id);
    if (!ids[key]) {
	  ids[key] = obj;
	} else {
 	  $debug.error("creating syncable already in map:" + obj);
	}
    return false;
  };
  
  /** return the javascript object tracked by this id pair */
  self.get = function(partitionId, instanceId) {
    return ids[self.instanceKey(partitionId, instanceId)];
  };
  
  /** return the javascript object tracked by this key (which embeds the id pair) */
  self.getByInstanceKey = function(key) {
  	return ids[key];
  };
  
  /** report whether an object is tracked by the syncable map */
  self.contains = function(partitionId, instanceId) {
    return ids.hasOwnProperty(self.instanceKey(partitionId, instanceId));
  };

  /** create a new object of the id and kind described in a template object.
   * The new object is placed in the id map, and connected to a prototype of
   * appropriate for the kind.
   * 
   * (data from the template isn't added here. it's added fieldwise by
   *  manager.update())
   * 
   * @param template.kind describes the type of the object to be created.
   *      template.kind e.g "sync.set" creates a new object by calling the function
   *          sync.set() with no parameters.
   *      template.id id of the object to be created.  
   *      template.$partition partitionId of the object to be created
   * @return newly created raw syncable object (no data fields have been set)
   */    
  self.createRaw = function(template) {
    var constructFn, obj, partition = template.$partition, id = template.id, kind = template.kind;
    
    $debug.assert(id);
    $debug.assert(!self.contains(partition, id));
    
    if (kind) {
      // get constructor function for this kind
      constructFn = constructors[kind];
	  $debug.assert(typeof(constructFn) === 'function');
      // construct a new object for this kind using the id from the template.id
	  self.withNewIdentity({partition: partition, id:id}, function() {
	  	obj = constructFn();
	  });
//	  $debug.log("createRaw() created: " + obj + " contains?=" +
//			  $sync.manager.contains(partition,id));
    }
    else {
      self.printLocal();
      $debug.fail("kind unspecified in createRaw: " + JSON.stringify(template, null, 2));
    }
    
    // (constructor above is expected to call createSyncable and put it in the map)
    $debug.assert(self.isSyncable(obj));
    return obj;
  };
  
  /** report the full instance key for a given object */ 
  self.instanceKey = function(partitionId, instanceId) {
  	return partitionId + "/" + instanceId;
  };
  
  /** set the default partition  
   * 
   * @param {String} partitionId  -- partition for newly created syncable objects 
   * @param {function} fn  -- (optional) function.  Set the defalut partition for new 
   * objects execute fn() and then set it back. 
   */
  self.setDefaultPartition = function(partitionId, fn) {
  	var oldDefault;
  	if (typeof(fn) !== 'undefined') {
 	  origDefault = defaultPartition;
	  defaultPartition = partitionId;
	  fn();
	  defaultPartition = origDefault;
	} else {
	  defaultPartition = partitionId;
	}
  };

  /** called when any object changes.  update the change set for
   * future commits() */
  function syncableChanged(change) {
    // ignore server generated changes
    if (change.source !== "server") {
//      $debug.log("adding change to changeSet: " + change);
      changeSet.push(change);
      // schedule a commit of changes to the server
      if (autoCommit && connection) {
        setTimeout($sync.manager.commit, 100);
      }
    }
    else {
//      $debug.log("skipping change (not adding to changeSet): " + change);
    }    
  }
  
  /** make up a unique string identifier for a new per instance kind.  
   * (CONSIDER removing support for these 'untyped' instances)
   */ 
  function synthesizeKind(id) {
    return "synth-" + id;
  }

  /** prototype of all syncable objects
   * (overrides toString for debug logging) 
   */
  var kindBasePrototype = {
    toString: function() {
      var kindStr = this.kind ? " (" + this.kind + ")" : "";
      var idStr = self.instanceKey(this.$partition, this.id);
      return idStr + kindStr;
    },
    
    setProperty: function(property, value) {
      this[property + "_"](value);
    }
  };
  
  /* get or make prototype for objects of this kind */
  function kindPrototype(kind) {
    var kindProto = kindPrototypes[kind];
    $debug.assert(kindProto !== undefined);
    return kindProto;
  }
  
  /** 
   * create a new protytpe object for instances of this kind
   * 
   * @param {Object} kind
   * @param {Object} model 
   */
  function makeKindPrototype(kind, model) {
    var kindProto = $sync.util.createObject(kindBasePrototype);
	kindProto.kind = kind;
	populateKindPrototype(kindProto, model);
    kindPrototypes[kind] = kindProto;
    return kindProto;
  };
  
  /** add field setters to the prototype for this kind */
  function populateKindPrototype(kindProto, model) {
    var prop;
	
    for (prop in model) {
      if (model.hasOwnProperty(prop) && !kindProto.hasOwnProperty(prop) 
	      && typeof model[prop] !== 'function' && !reservedProperty(prop)) {
        addAccessor(kindProto, prop);		  	
	  }
    }
	// CONSIDER allowing functions in the model.  they become methods on this kind
  };
  
  /** properties of syncable objects used by the framework, clients shouldn't
   * use these.  
   * 
   * SOON make all properties that begin with '$' reserved, CONSIDER per class reserves
   */
  function reservedProperty(prop) {
    if (prop === "id" || prop === "kind" || prop === "$partition") {
      return true; 
    }
    return false;
  };

  /** add a property setter to an observable object.  The
   * setter is the property name with an underscore appended.
   *
   * The setter function notifies any observers when called.
   *
   * @param obj  target object
   * @param prop  property name
   */
  function addAccessor(obj, prop) {
    var setter = prop + "_";
    
    if (obj.prop) {
      $debug.assert(typeof obj[prop] !== 'function');
      $debug.assert(typeof obj[setter] === 'function');
    }
    else {
      // generic setter function for a property
      obj[setter] = function(value) {
        var oldValue = this[prop];
        this[prop] = value;
        
        // notify
        $sync.observation.notify(this, "property", {
          property: prop,
          oldValue: oldValue
        });
        
        // support fluid style, e.g. obj.foo_().bar_()
        return this;
      };
      
	  // (Users should use "obj.prop_(val)"
      // not "obj.prop = val".  the latter form would be nice, but we can't override
      // setters on older IE browsers.)
	  //
      // LATER in browsers which support getter/setter (e.g. firefox, IE8) create a
      // setter function which fires and an assertion.  
      // 
      // (LATER consider using eval to create a custom function with the property
      //  inserted for speed)
    }
  };

  /** process changes to a sync.set */
  function editSet(set, changes) {
    var changeDex, putDex, obj, puts, onePut, change;
    
    for (change in changes) {
      if (change === "put") {
        // process something like  "put": [{id:2, $partition:"part1"},{id:3, $partition:"part1"}]
        puts = changes.put;
        for (putDex = 0; putDex < puts.length; putDex++) {
	      onePut = puts[putDex];
          obj = self.get(onePut.$partition, onePut.id);
          $debug.assert(obj);
          // these changes came from the server, no sense sending them back)
          $debug.assert($sync.observation.currentMutator() === "server");
          set.put(obj)
        }
      }
    }
  }
  
  self.reset();

  return self;
}();

