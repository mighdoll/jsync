test("twoBrowsers.changeName", function() {
  expect(1);
  withTestSubscription("twoBrowsers", start, verify);
  
  function start(twoBrowsers) {
    twoBrowsers.cmd_('namedFred');
    twoBrowsers.obj_($sync.test.nameObj());
  }
  function verify(change) {
    var two = change.target;
    ok(two.obj.name === 'fred');
  }
});

