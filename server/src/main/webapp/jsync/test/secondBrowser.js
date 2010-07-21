
(function() {
  var log = $log.logger('secondBrowser');
  setup();
  
  function setup() {
    $sync.manager.setDefaultPartition("test");
    $sync.subscribe('/test/sync', 'twoBrowsers', subscribed);
    function subscribed(two) {
      log.detail('subscribed');
      two.cmdChanges().watch(function(change) {
        log.detail("change arrived:", change);
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
      log.detail("namedFred change on: ", two.obj);
      two.obj.name_('fred');
    }
  };
  
  
})();