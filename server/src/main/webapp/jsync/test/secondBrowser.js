
(function() {
  setup();
  var log = $log.logger('secondBrowser');
  
  function setup() {
    $sync.manager.setDefaultPartition("test");
    $sync.subscribe('/test/sync', 'twoBrowsers', subscribed);
    function subscribed(two) {
      two.cmdChanges().watch(function(change) {
        log.debug("change arrived:", change);
        runCmd(two);
      });
    }
  }
  
  function runCmd(two) {
    var fn = cmds[two.cmd];
    fn && fn(two);
  }
  
  var cmds = {
    namedFred: function(two) {
      log.debug("making change");
      two.obj.name_('fred');
    }
  };
  
  
})();