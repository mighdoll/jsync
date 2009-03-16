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
 * id is guaranteed to be a unique, and is currently implemented a string.
 * 
 * kind is akin to the class of the object - the client typically uses the kind to
 * attach functions to the data object arriving over the wire. 
 *
 * We maintain an mapping table that maps unique ids to javascript objects.  Objects
 * are entered into the table when they are first received from the network.  LATER
 * note that currently the table is not garbage collected, so syncable objects
 * are never freed.
 */

var $sync = $sync || {};

$sync.manager = function() {
    var that = {},
    ids,           // mapping of ids to objects
    connection,    // for now, the browser has only one connection
    changeSet,     // uncommitted changes to be sent to the server on commit()
    nextId,        // next id to use for locally created objects
    clientId = "Browser-",  // id prefix for locally created objects LATER protocol should give a unique id for this session
    kindPrototypes, // map of prototype objects for each kind, indexed by kind string
    inUpdate;      // true while we're processing server delivered changes
	
    /* reset to initial state (useful for testing!) */
    that.reset = function() {
        ids = {};
        inUpdate = false;
        connection = null;
        changeSet = [];
        kindPrototypes = {};
        nextId = 0;
        $sync.notification.reset();
        $sync.notification.watchEvery(syncableChanged, "$sync.manager");
    };   
	
    /* Instantiate a new syncable object
     * 
     * @param kind - specifies the syncable kind for the syncable object
     *    kind typically refers to a constructor function, which will be called
     *    when objects of this kind are received from the network.
     * @param dataModel - optional model for this object.  accessors for each property
     *   in the template are added to the prototype for all objects of this kind.
     *   the data for each property in the template is copied to the new syncable
     *   instance.
     * @param params - insantatiation parameters passed to constructor
     *   when objects are instantiated by the manager from the network
     */
    that.createSyncable = function(kind, dataModel, params) {
        var kindProto, obj, id;

        // get id for new object
        if (params && params.$syncId)
            id = params.$syncId;
        else
            id = clientId + nextId++;

        // get kind for new object
        if (!kind) {        // use the provided kind
            if (dataModel && dataModel.kind)
                            // or use the kind from the data model
                kind = dataModel.kind;
            else            // otherwise, invent a unique kind for this instance
                kind = synthesizeKind(id); }

        // create a javascript instance with appropriate prorotype for this kind
        kindProto = kindPrototype(kind),
        obj = $sync.util.createObject(kindProto);

        // set id and kind in object
        obj.kind = kind;
        obj.id = id;

        // setup data fields in new object (if a model is provided)
        if (dataModel) {
            $sync.util.copyObjectData(dataModel, obj);
            populateKindAccessors(obj.kind, obj);
        }

        // install in map and let people know.  we've made a new syncable!
        that.put(obj) || $debug.error("creating syncable already in map:" + obj);
        $sync.notification.notify(obj, {changeType: "create"});
        return obj;
    };

    /* refresh the schema and accessors based on the data fields in this object */
    that.updateKind = function(model) {
        populateKindAccessors(model.kind, model);
    };

    /* send all modified objects to the server */
    that.commit = function() {
        connection.sendModified(changeSet);
        changeSet = []; };
    
    /* register an existing sync channels */
    that.registerConnection = function(conn) {
        connection = conn; };

    /* called when any object changes, update the change set for
     * future commits() */
    var syncableChanged = function(obj, changes) {
        // add local changes to changeSet (we ignore server generated edits
        // they don't )
        if (!inUpdate) {
            changeSet.push({obj:obj, changes:changes}); }};

    /* make up a new per instance kind */
    var synthesizeKind = function(id) {
        return "synth-"+id; };

/*
    var synthsizeKindConstructor = function(kind, model) {
        return function(params) {
            return $sync.manager.createSyncable(kind, model, params); } }
*/

    /* add obj to the map unless the map already has an object at that id
     *
     * @param obj object to add
     * @param force  add new object even if there's another object in the map. */
    that.put = function(obj, force) {
        if (!ids[obj.id] || force) {
            ids[obj.id] = obj;
            return true; }        
        return false; };

    /* return the javascript object tracked by this id */
    that.get = function(id) {
        return ids[id]; };

    /* report whether an object is tracked by the syncable map */
    that.contains = function(id) {
        return ids.hasOwnProperty(id); };

    /* create a new object of the id and kind described in a template object.
     * The new object is placed in the id map, and connected to a prototype of
     * appropriate for the kind.
     * 
     * (data from the template isn't added here. it's added fieldwise by
     *  manager.update())
     *
     * we try to find the appropriate constructor from an existing syncable
     * class to do the work for us.  If no appropriate constructor is found,
     * (e.g. because the object is mi ssing) we synthesize one.
     * 
     * @param template.kind describes the type of the object to be created.
     *      template.kind e.g "sync.set" creates a new object by calling the function
     *          sync.set() with no parameters.
     *      template.id id of the object to be created.  (id is presumed to be unique)
     * @return newly created raw syncable object (no data fields have been set)
     */    
    that.createRaw = function(template) {
        var constructFn, obj, id = template.id, kind = template.kind;
        
        $debug.assert(id);
        $debug.assert(!that.contains(id));

        if (kind) {
            // get constructor function for this kind
            constructFn = findKindConstructor(kind);
            // construct a new object for this kind using the id from the template.id
            obj = constructFn({$syncId:id}); }
        else {
            // createSyncable will synthesize a new kind for this instance
            obj = that.createSyncable(null, template, {$syncId:id});
        }

        // (constructor above is expected to call createSyncable and put it in the map)
        $debug.assert(that.isSyncable(obj));
        return obj; };


    var findKindConstructor = function(kind) {
        var kinds, fn;

        // use kind to find an object creation function e.g. com.foo.person()
        fn = window;
        kinds = kind.split('.');
        for (i = 0; i < kinds.length; i++)
            fn = fn[kinds[i]];

        if (typeof fn !== 'function') {
            fn = null;
            $debug.warn("constructor not found for kind: " + kind); }

        return fn;
    }


    var kindBasePrototype = {
        toString: function() {
            var kindStr = this.kind ? " (" + this.kind + ")" : "";
            var idStr = "#" + this.id;
            return idStr + kindStr;
        }
    };

    /* get or make prototype for objects of this kind */
    var kindPrototype = function(kind) {
        var kindProto = kindPrototypes[kind];
        if (!kindProto)
            kindProto = makeKindPrototype(kind);
        return kindProto;}
    
        // create a new protytpe for this kind
    var makeKindPrototype = function(kind) {
        var kindProto = $sync.util.createObject(kindBasePrototype);
        kindPrototypes[kind] = kindProto;
        return kindProto;
    };

    /* add field setters to the prototype for this kind */
    var populateKindAccessors = function(kind, model) {
        var prop, kindProto;

        kindProto = kindPrototype(kind);
        // add any newly discovered accessors to the kind prototype
        for (prop in model) {
            if (model.hasOwnProperty(prop) 
                && !kindProto.hasOwnProperty(prop)
                && typeof model[prop] !== 'function'
                && !reservedProperty(prop))
                   addAccessor(kindProto, prop); } };

    /* properties of syncable objects used by the framework, clients shouldn't
     * use these.  */
    var reservedProperty = function(prop) {
        if (prop === "id" || prop === "kind") {
            return true;
        }
        return false;
    };

    /* add a property setter to an observable object.  The
     * setter is the property name with an underscore appended.
     *
     * The setter function notifies any observers when called.
     *
     * @param obj  target object
     * @param prop  property name
     */
    var addAccessor = function(obj, prop) {
        var setter = prop + "_";
		
        if (obj.prop) {
            $debug.assert(typeof obj[prop] !== 'function');
            $debug.assert(typeof obj[setter] === 'function');
        }
        else {
            // generic setter function for a property
            obj[setter] = function(value){
                var oldValue = this[prop];
                this[prop] = value;

                // notify
                $sync.notification.notify(this, {
                    changeType:"property",
                    property:prop,
                    oldValue:oldValue
                });

                // support fluid style, e.g. obj.foo_().bar_()
                return this;
            };
			
        // TODO in browsers which support getter/setter (e.g. firefox, IE8) create a
        // setter function which fires and an assertion.  (Users should use "obj.prop_(val)"
        // not "obj.prop = val".  the latter form would be nice, but we can't override
        // setters on older IE browsers.
        // 
        // (LATER consider using eval to create a custom function with the property
        //  inserted for speed)
        }
    };

    /* update a syncable object by copying fieldwise from a received data object.
     * the target object is identified by the id of the received object
     *
     * assumes that there is syncable object with the same id as received.
     */
    that.update = function(received) {
        var prop, obj = that.get(received.id);
        
        $debug.assert(obj && obj !== received);
        $debug.assert(obj.id === received.id);
        $debug.assert(!received.kind || obj.kind === received.kind);

        // update the kind prototype to take into account any new fields
        populateKindAccessors(received.kind, received);

        // update the target object with the received data
        for (prop in received) {
            // expecting only data fields in the received object
            $debug.assert(typeof received[prop] !== 'function');
				
            // call setter to set to received value. This notifies client observers
            // of the change but doesn't queue changes for the server (the
            // changes came from the server, no sense sending them back)
            if (!reservedProperty(prop))
                silentUpdate(function() {
                   obj[prop+"_"](received[prop])}); }};
   
    /* process a "#edit" object in the sync feed.
     * #edit objects contain:
     * here's an example edit object:
         { "#edit" : 1,              // target object id
          "edits" : [ {"put" : [2,3]} ]},   // changes to make to the target obj
     * 
     * TODO--move this protocol related stuff to connection.js
     */
    that.metasync = function(edit) {
        var target = that.get(edit["#edit"]);

        if (edit.edits) {
            if (target.kind === "$sync.set") {
                syncSet(target, edit.edits);
            } else {
                $debug.log("unexpected kind of _sync object");
            }
        }
    };

    /* don't collect object/collection modification changes while func()
     * is being processed
     */
    var silentUpdate = function(func) {
        inUpdate = true;
        func();
        inUpdate = false;
    }

    /* process changes to a sync.set */
    var syncSet = function(set, changes) {
        var changeDex, putDex, obj, puts, change;

        for (changeDex = 0; changeDex < changes.length; changeDex++) {
            change = changes[changeDex];
            // process something like  "put": [2,3]
            if (change.hasOwnProperty("put")) {
                puts = change.put;
                for (putDex = 0; putDex < puts.length; putDex++) {
                    obj = that.get(puts[putDex]);
                    $debug.assert(obj);
                    silentUpdate(function() {
                        set.put(obj)} );
                    inUpdate = false;
                }
            }
        }
    };

    /** @return true iff obj has the requisite sync fields (kind and id) and
     * registered with the sync manager */
    that.isSyncable = function(obj) {
        if (!obj.id || !obj.kind) {
            return false;
        }
        return that.get(obj.id) === obj;
    };

    that.reset();

    return that;
}();

