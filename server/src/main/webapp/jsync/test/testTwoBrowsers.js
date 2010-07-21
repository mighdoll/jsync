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
    
    unloadIFrame();
  }
});

function loadIframe() {
  $('<iframe/>', {
    id:'separateFrame',
    'class':'offscreen', 
    src:'testFrame.html'
  }).appendTo('body');
}

function unloadIFrame() {
  $('#separateFrame').html("");
}