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
 * All syncable javascript objects contain an '$id' property and a '$kind' property.
 * $id+$kind is guaranteed to be a unique, and is currently implemented as a string.
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
  var log = $log.logger("manager");
  var self = {};
  var ids;              // mapping of partition,ids to objects
  var connection;       // for now, each browser tab has only one connection to the service
  var changeSet;        // uncommitted changes to be sent to the server on commit()
  var nextId;           // next id to use for locally created objects
  var clientId = "Uninitialized-"; // id prefix for locally created objects LATER protocol should give a unique id for this session
  var kindPrototypes = {};  // map of prototype objects for each kind, indexed by kind string
  var constructors = {};// map of instance constructors, indexed by kind.
  var defaultPartition; // create object in this partition by default
  var nextIdentity;		// set the next instance ids to the contained {partition:, $id:} pair
  var autoCommit;		// automatically commit changes to the server if this is set
  var dirty;			// set to true when local objects have been changed and need to be sent to the server
  var commitTimers;     // pending commit() timers from object dirty notification
  var noticeDisabled;   // property setting temporarily disabled
  var directSetOK;      // temporarily turn off warnings on direct set of property value
  var debugMode;        // true to generate assertions for inappropriate use of accessors (works on firefox)
  var debugModeAccessor;// func that generates asserting accessors, or noop if !debugMode
  

  /** reset to initial state (useful for testing!) */
  self.reset = function() {
    log.detail("$sync.manager.reset()");
    ids = {};
    defaultPartition = "partition-unset";
    connection && connection.close({
      ignoreMessages: true
    });
    connection = null;
    changeSet = [];
    nextId = 0;
    dirty = false;
    nextIdentity = undefined;
    self.transientPartition = undefined;
    autoCommit = true;
    cancelCommitTimers();
    self.debugMode(true); 
    $sync.observation.reset();
    $sync.observation.watchEvery(syncableChanged, "$sync.manager");
  };

  self.autoCommit = function(auto) {
    if (auto === undefined) {
      autoCommit = true;
    } else {
      autoCommit = auto;
    }
  };

  /** @return true iff obj has the requisite sync fields ($kind and $id) and
   * registered with the sync manager */
  self.isSyncable = function(obj) {
    if (!obj.$id || !obj.$kind) {
      return false;
    }
    return self.get(obj.$partition, obj.$id) === obj;
  };

  /**
   * Set the identity (partition, $id) for the next created object
   *
   * used when instantiating remote objects
   *
   * @param {Object} ids
   * @param {Object} fn
   */
  self.withNewIdentity = function(ids, fn) {
    var oldIds = nextIdentity;
    var result;
    nextIdentity = ids;
    result = fn();
    nextIdentity = oldIds;
    return result;
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
//  	log.detail("defineKind: " + kind);
    if (kindPrototypes[kind] !== undefined) {
      $debug.fail("defineKind called twice on kind: " + kind);
      return kindPrototypes[kind];
    }
    if (dataModel instanceof Array) { 	  // convert array of property names to hash
      var model = {};
      $.each(dataModel, function(_, name) {
        model[name] = null;
      });
      dataModel = model;
    }

    var proto = makeKindPrototype(kind, dataModel);
    constructors[kind] = constructor = providedConstructor || constructor;
    constructor.kindPrototype = proto;
    $.extend(constructor, {
      classMethods: function(methods) {
        $.extend(constructor, methods);
      },
      instanceMethods: function(methods) {
        $.extend(proto, methods);
      }
    });
    return providedConstructor ? null : constructor;

	/**
	 * Constructor for this kind of object
	 *
	 * @param {Object} instanceData - hash of {property:value} initial values
	 * for the new instance
	 * @param {String} ?options- {partition:string} in which to create the new object
	 */
    function constructor(instanceData, options) {
      if (options && options.partition) {
        return $sync.manager.withPartition(options.partition, function() {
          return $sync.manager.createSyncable(kind, instanceData);
        });
      } else {
        return $sync.manager.createSyncable(kind, instanceData);
      }
    }
  };

  /** Instantiate a new syncable object.  This creates a prototype, and
   * instantiates it.  A subsequent call with the same kind or dataModel.kind
   * will use the same prototype.
   *
   * @param kind : ?string - specifies the syncable kind for the syncable object
   *    kind typically refers to a constructor function, which will be called
   *    when objects of this kind are received from the network.  defaults to
   *    dataModel.$kind.
   * @param instanceData - optional instance data copied to this instance.
   */
  self.createSyncable = function(kind, instanceData) {
    var kindProto, obj;

    // create a javascript object instance with appropriate prototype for this kind
    kindProto = kindPrototype(kind);
    obj = $sync.util.createObject(kindProto);
    
    // setup identity for this object
    if (nextIdentity) { // identity is preset, probably arriving over the network
      obj.$partition = nextIdentity.partition;
      obj.$id = nextIdentity.$id;
      nextIdentity = undefined;
    } else {
      obj.$partition = defaultPartition;
      obj.$id = clientId + nextId++;
    }

    $debug.assert(obj.$partition !== "partition-unset");

  	// populate with instance data
  	instanceData && self.quietCopy(obj, instanceData);

    // user defined initialization
    if (typeof obj._init === 'function') {
      obj._init();
    }

    // install in map and let people know.  we've made a new syncable!
    self.put(obj);
//	log.detail("createdSyncable: ", obj);
    $sync.observation.notify(obj, "create");
    return obj;
  };

  /** send all modified objects to the server */
  self.commit = function() {
    if (!connection) {
      $debug.fail("manager().commit, but connection is: ", connection, " changeSet: ", changeSet);
    } else {
      if (changeSet.length > 0) {
        connection.sendModified(changeSet);
        changeSet = [];
      }
    }
  };

  /** register an existing sync channel */
  self.registerConnection = function(conn, id) {
    connection = conn;
    clientId = "*" + id;
    self.transientPartition = id;
  };

  /** handy access to the current connection */
  self.connection = function() {
  	return connection;
  };

  /** log the entire local syncable instance table for debugging */
  self.printLocal = function() {
    log.log("local syncable instances:");
    for (var id in ids) {
      if (typeof id !== 'function' && ids.hasOwnProperty(id)) {
         log.log("  ", ids[id]);
      }
    }
  };

  /** add obj to the map unless the map already has an object at that ($id,$partition)
   *
   * @param obj object to add
   */
  self.put = function(obj) {
//  	log.trace("$sync.manager.put: ", obj);
  	var key = compositeKey(obj.$partition, obj.$id);
    if (!ids[key]) {
	  ids[key] = obj;
	} else {
 	  log.error("creating syncable already in map:" + obj);
	}
    return false;
  };

  /** return the javascript object tracked by this id pair */
  self.get = function(partitionId, instanceId) {
    return ids[compositeKey(partitionId, instanceId)];
  };
  
  /** report whether an object is tracked by the syncable map */
  self.contains = function(partitionId, instanceId) {
    return ids.hasOwnProperty(compositeKey(partitionId, instanceId));
  };

  /** create a new object of the id and kind described in a template object.
   * The new object is placed in the id map, and connected to a prototype of
   * appropriate for the kind.
   *
   * (data from the template isn't added here. it's added fieldwise by
   *  update.updateInstance())
   *
   * @param template.$kind describes the type of the object to be created.
   *      template.$kind e.g "sync.set" creates a new object by calling the function
   *          sync.set() with no parameters.
   *      template.$id id of the object to be created.
   *      template.$partition partitionId of the object to be created
   * @return newly created raw syncable object (no data fields have been set)
   */
  self.createRaw = function(template) {
    var constructFn, obj, partition = template.$partition, id = template.$id, kind = template.$kind;

    $debug.assert(id);
    $debug.assert(!self.contains(partition, id));

    if (kind) {
      // get constructor function for this kind
      constructFn = constructors[kind];
	  if (typeof(constructFn) !== 'function') {
	  	$debug.fail("constructor not defined for kind: " + kind);
	  }
      // construct a new object for this kind using the id from the template.id
	  self.withNewIdentity({partition: partition, $id:id}, function() {
	  	obj = constructFn();
	  });
//	  log.log("createRaw() created: ", obj, " contains?=" +
//			  $sync.manager.contains(partition,id));
    }
    else {
      self.printLocal();
      $debug.fail("createRaw() kind unspecified for: ", template);
    }

    // (constructor above is expected to call createSyncable and put it in the map)
    $debug.assert(self.isSyncable(obj));
    return obj;
  };

  /** report the full instance key for a given object.  
   * LATER move this to syncable prototype e.g. obj._id() */  
  self.instanceKey = function(obj) {
    return compositeKey(obj.$partition, obj.$id);
  };
  
  function compositeKey(partitionId, instanceId) {
  	return partitionId + "/" + instanceId;
  };

  /** set the default partition
   *
   * @param {String} partitionId  -- partition for newly created syncable objects
   */
  self.setDefaultPartition = function(partitionId) {
    $debug.assert(partitionId !== undefined, 'setDefaultPartition(undefined)');
    defaultPartition = partitionId;
  };

  /** set the default partition temporarily while running a function.
   * (handy for creating objects in a certain partition)
   *
   * @param {String} partitionId  -- partition for newly created syncable objects
   * @param {function} fn  -- function.
   * @return the result of fn()
   */
  self.withPartition = function(partitionId, fn) {
    var result;
    var orig = defaultPartition;
    self.setDefaultPartition(partitionId);
    result = fn();
    self.setDefaultPartition(orig);
    return result;
  };

  /** run a function with the default partition set temporarily to
   * to a transient partition (not persisted between client sessions)
   * (handy for creating objects in a the transient partition)
   * @return the result of fn()
   */
  self.withTransientPartition = function(fn) {
//  log.detail("transientPartition: " + self.transientPartition);
  	return self.withPartition(self.transientPartition, fn);
  };
  
  /**
   * 
   */
  self.withNoNotice = function(fn) {
    var oldNoticeDisabled = noticeDisabled;
    noticeDisabled = true;
    var result = fn();
    noticeDisabled = oldNoticeDisabled;
    return result;
  };
  
  /**
   * call fn with the name of each property in a syncable
   */
  self.eachPropertyName = function(syncable, fn) {
    var kindProto = kindPrototype(syncable.$kind);
    for (var propName in kindProto) {
      if (typeof kindProto[propName] == 'function' 
        && propName[propName.length - 1] == '_') {
        fn(propName.slice(0,propName.length - 1));
      }
    }
  };
  
  /** properties of syncable objects used by the framework, clients shouldn't
   * use these.
   *
   * SOON make all properties that begin with '$' reserved, CONSIDER per class reserves
   */
  self.reservedProperty = function(prop) {
    if (prop === "$id" || prop === "$kind" || prop === "$partition") {
      return true;
    }
    return false;
  };


  /** cancels all pending commit() timers.  
   * (When objects are modified, a timer is set to commit the changes to the service.) */
  function cancelCommitTimers() {
    if (commitTimers) {
      $.each(commitTimers, function() {
        log.detail("cancelling timeout: ", this);
        clearTimeout(this);
      });
    }
    commitTimers = [];
  }

  /** called when any object changes.  update the change set for
   * future commits() */
  function syncableChanged(change) {
    // ignore server generated changes
    if (change.source !== "server") {
      log.detail("adding change to changeSet: ", change);
      changeSet.push(change);
      // schedule a commit of changes to the server
      if (autoCommit && connection && !dirty) {
        dirty = true;
        commitTimers.push(setTimeout(function() {
          $sync.manager.commit();
          dirty = false;
        }, 25));
      }
    }
    else {
//      log.detail("skipping server change (not adding to changeSet): " + change);
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
      var kindStr = this.$kind ? " (" + this.$kind + ")" : "";
      var idStr = compositeKey(this.$partition, this.$id);
      return idStr + kindStr;
    },

    setProperty: function(property, value) {
      this[property + "_"](value);
    }
  };  

  /* get prototype for objects of this kind */
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
    kindProto.$kind = kind;
    populateKindPrototype(kindProto, model);
    kindPrototypes[kind] = kindProto;
    return kindProto;
  }

  /** add field setters to the prototype for this kind */
  function populateKindPrototype(kindProto, model) {
    var prop;
    
    for (prop in model) {
      if (model.hasOwnProperty(prop) && !kindProto.hasOwnProperty(prop) &&
      typeof model[prop] !== 'function' && !self.reservedProperty(prop)) {
        addAccessor(kindProto, prop);
      }
    }
    
    // property that returns a Changes object that generates events when any property changes
    kindProto.$allChanges = function() {
      return propertyChanges(this, '$all');
    };

    // CONSIDER allowing functions in the model.  they become methods on this kind
  }

  /** Set a property w/o using the setter function, and thus not triggering property change dection. */
  self.quietSetProperty = function(obj, prop, value) {
    var oldDirectSet = directSetOK;
    directSetOK = true;
    obj[prop] = value;
    directSetOK = oldDirectSet;
  };
  
  /** copy fields w/o triggering change notification.  */
  self.quietCopy = function(target, src) {
    $.each(src, function(prop, value) {
      self.quietSetProperty(target, prop, value);
    });
  };
  
  /** copy fields with change notification */
  self.copyProperties = function(target, src) {
    $.each(src, function(prop, value) {
      target.setProperty(prop, value);
    });
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
    } else {
      // generic setter function for a property
      obj[setter] = function(value) {
        var oldValue = this[prop];
        self.quietSetProperty(this, prop, value);

        // notify
        if (!noticeDisabled) {
          $sync.observation.notify(this, "property", {
            property: prop,
            oldValue: oldValue
          });
        }
        
        // support fluid style, e.g. obj.foo_().bar_()
        return this;
      };
      
      debugModeAccessor(obj, prop);
      
      // return a Changes object that generates events when a given property changes
      obj[prop+"Changes"] = function() {
        return propertyChanges(this, prop);
      };
      
    }
  }
  
  /** return a Changes object so that callers can watch() it for changes 
   * to the underlying property.
   * Creates the Changes object if necessery. */
  function propertyChanges(obj, prop) {
    var changesProp = changesProperty(prop);
    var changes = obj[changesProp] = obj[changesProp] || $sync.propertyChanges(obj, prop);    
    return changes;
  }
  

  /** return the name of the property for watching changes */
  function changesProperty(propertyName) {
    return "$" + propertyName + "Changes";
  }
  
  /** 
   * Return a readonly Changes object. It hashas the registered watchers
   * for the given property.  Use the property '$all' to watch all properties.
   * 
   * (if there are no watchers, it returns a read 
   * only blank rather than creating a new Changes object) 
   */
  self.propertyChangesReadOnly = function(obj, propertyName) {
    var changesProp = changesProperty(propertyName);
    return obj[changesProp] || $sync.changes.blank();
  };
  
  /** set debug mode, which warns if raw property setters are used on a syncable. */
  self.debugMode = function(mode) {
    debugMode = mode === undefined ? true : mode;
    if (debugMode && $.isFunction(Object.__defineSetter__)) {
      debugModeAccessor = defineDebugAccessor;
    } else {
      debugModeAccessor = function() {};
    }
    
    function defineDebugAccessor(obj, prop) {
      obj.__defineSetter__(prop, function(value) {
        if (directSetOK) {
          this['_' + prop] = value;
        } else {
          log.fail("Use obj.prop_(value) for changing property values", obj, prop, value);
        }
      });
      obj.__defineGetter__(prop, function() {
        return this['_'+prop];
      });
    }  // LATER use defineProperty if available
  };

  self.reset();

  return self;
}();
