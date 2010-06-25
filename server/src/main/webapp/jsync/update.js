/**
 * Apply updates to objects and collections.
 * 
 * 
 */
var $sync = $sync || {};
$sync.update = $sync.update || {};

(function() {
  var log = $log.logger("$sync.update");
  /** update a syncable object by copying fieldwise from a received data object.
   * the target object is identified by the id of the received object
   *
   * assumes that there is syncable object with the same id as received.
   */
  $sync.update.updateInstance = function(received) {
    var prop, obj = $sync.manager.get(received.$partition, received.$id);

//    log.detail("manager.update: ", received);
    $debug.assert(obj && obj !== received);
    $debug.assert(obj.$id === received.$id);
    $debug.assert(!received.$kind || obj.$kind === received.$kind);

    // update the target object with the received data
    for (prop in received) {
      // expecting only data fields in the received object
      $debug.assert(typeof received[prop] !== 'function');

      // call setter to set to received value. This notifies client observers
      // of the change.
      if (!$sync.manager.reservedProperty(prop)) {
        // these changes came from the server, no sense sending them back
        $debug.assert($sync.observation.currentMutator() === "server");
        obj[prop + "_"](received[prop]);
      }
    }
  };

  /** process a "#edit" object in the sync feed.
   * #edit objects contain:
   * here's an example edit object:
   *   { "#edit" : {$id:1, $partition:"test"},         // target object id
   *                        // put objects 2,3 into object 1
   *     "put": [{$id:2, $partition:"test"}, {$id:3, $partition:"test"}]
   *
   * CONSIDER--move this protocol related stuff to connection.js ?
   */
  $sync.update.updateCollection = function(edit) {
    var editRef = edit["#edit"];
    var collection = $sync.manager.get(editRef.$partition, editRef.$id);
    if (typeof(collection) === 'undefined') {
      log.error("target of collection edit not found: ", edit);
    } else if (collection.$kind === "$sync.set") {
      editSet(collection, edit);
    } else if (collection.$kind === "$sync.sequence") {
      editSequence(collection, edit);
    } else {
      log.error("unexpected $kind of collection for #edit: ", edit, " found: ", collection);
    }
  };
  

  /** process changes to a sync.set */
  function editSet(set, changes) {
    var changeDex, putDex, obj, puts, onePut, change;

    for (change in changes) {
      if (change === "put") {
        // process something like  "put": [{$id:2, $partition:"part1"},{$id:3, $partition:"part1"}]
        puts = changes.put;
        for (putDex = 0; putDex < puts.length; putDex++) {
          onePut = puts[putDex];
          obj = $sync.manager.get(onePut.$partition, onePut.$id);
          $debug.assert(obj);
          // these changes came from the server, no sense sending them back)
          $debug.assert($sync.observation.currentMutator() === "server");
//          log.detail("manager.editSet: put", onePut, " into: ", set);
          set.put(obj);
        }
      }
    }
  }

  function editSequence(seq, edit) {
    if (edit.insertAt !== undefined) {
      var insertDex, elem, insert;
      var insertAt = edit.insertAt;
      
      $debug.assert($sync.util.isArray(insertAt));
      for (insertDex = 0; insertDex < insertAt.length; insertDex++) {
        insert = insertAt[insertDex];
        elem = $sync.manager.get(insert.elem.$partition, insert.elem.$id);
        seq.insertAt(elem, insert.at);
      }
    }
    else if (edit.move !== undefined) { // CONSIDER should we allow arrays here in the protocol?
      var move = edit.move;
      seq.moveAt(move.from, move.to);
    }
    else if (edit.removeAt !== undefined) {
      seq.removeAt(edit.removeAt);
    }
  }
  
})();

