var $sync = $sync || {};

/** event stream of changes.*/
$sync.changes = function() {
  var self = {
    _watchers: [],
    watch : function(fn) {
      self._watchers.push(fn);
      return self;
    },
    notify : function(change) {
      log.debug("not currently used");
      $.each(self._watchers, function(fn) {
        fn(change);
      });
      return self;
    },
    destroy: function() {
      _watchers = [];
    }
  };
  return self;
  
  // LATER consider frp style combinators
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
