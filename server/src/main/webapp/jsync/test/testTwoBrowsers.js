test("twoBrowsers.changeName", function() {
  expect(1);

  withTestSubscription("twoBrowsers", start, verify);
  loadIframe();
  
  function start(two) {
    two.cmd_('namedFred');
    two.obj_($sync.test.nameObj());
  }
  function verify(change) {
    var named= change.target;
    ok(named.name === 'fred');
  }
});


function loadIframe() {  
  $('#separateFrame').attr('src', 'testFrame.html');
}   