loggedTest("twoBrowsers.changeName", function() {
  loadIframe();
  expect(1);

  withTwoBrowsers(start, verify);
  
  function start(two) {
    two.cmd_('namedFred');
    two.obj_($sync.test.nameObj());
  }
  function verify(named) {
    ok(named.name === 'fred');    
  }
});

loggedTest("twoBrowsers.seq", function() {
  loadIframe();
  expect(1);
  
  withTwoBrowsers(start, verify);
  
  function start(two) {
    var seq = $sync.sequence(); 
    seq.append($sync.test.nameObj());
    two.cmd_('removeFromSeq');
    two.obj_(seq);
  }
  function verify(seq) {
    ok(seq.size() === 0);    
  }  
});


function withTwoBrowsers(startFn, verifyFn) {
  withTestSubscription("twoBrowsers", startFn, verifyUnload);
  
  function verifyUnload(change) {
    verifyFn(change.target);
  }  
}


function loadIframe() {
  if ($('#separateFrame').size() === 0) {
    $('<iframe/>', {
      id:'separateFrame',
      'class':'offscreen', 
      src:'testFrame.html'
    }).appendTo('body');
  }
}

function unloadIFrame() {
  $('#separateFrame').html("");
}