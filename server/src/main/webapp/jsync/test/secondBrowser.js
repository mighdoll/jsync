
(function() {
  var log = $log.logger('secondBrowser');
//  log.setLevel('detail');
//  $log.logger('manager').setLevel(0);
//  $log.logger('receive').setLevel(0);
//  $log.logger('connection').setLevel(0);
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
      log.detail("namedFred changing: ", two.obj);
      two.obj.name_('fred');
    },
    removeFromSeq: function(two) {
      log.detail("removeFromSeq changing: ", two.obj);
      two.obj.removeAt(0);
    }
  };
  
  
})();