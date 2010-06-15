var $sync = $sync || {};

/** event stream of changes.*/
$sync.changes = function() {
  var self = {
    _watchers: [],
    // TODO record the taget object
    watch : function(fn) {
      self._watchers.push(fn);
      return self;
    },
//    notify : function(change) {
//      log.debug("not currently used");  // observe.js currently calls _watchers directly
//      $.each(self._watchers, function(fn) {
//        fn(change);
//      });
//      return self;
//    },
    destroy: function() {
      _watchers = [];
    }
  };
  return self;  
  // LATER consider frp style combinators
};

/** Event stream of changes to a properties.  Synthesizes an 'initial' state change for
 * each new watcher.
 */
$sync.propertyChanges = function(target, property) {
  var parent = $sync.changes();
  var self = $sync.util.createObject(parent);
  self.target = target;
  self.property = property;
  self.watch = function(fn) {
    parent.watch(fn);
    var initState = $sync.changeDescription('initial', target, {property:property});
    fn(initState);
    return self;
  };
  return self;
};

/** empty changes, read only */
(function() {
  var log = $log.logger("changes");
  var blank = $sync.changes();
  blank.watch = function() {
    log.fail("don't call watch $sync.changes.blank()");
  };
  $sync.changes.blank = function() {return blank;};
})();
