$(function() {
  var settings, demoWindow;
  
  function subscribe() {
    registerButtons();
//    connection.subscribe("GuestSettings", "guests", registerButtons);
  }
  function registerButtons() {
    $('#tryIt').click(tryIt);
  }
  
  /** Open a browser window to show the demo results.  */
  function showDemoWindow(target) {
    /** position the demo results window to the right of the main window.  LATER, test this under IE */
    function placeDemoWindow() {
      var x = window.screenX != undefined ? window.screenX : window.screenLeft;
      var y = window.screenY != undefined ? window.screenY : window.screenTop;
      var margin = 20;
      x += window.outerWidth + margin;
      return {x: x, y:y};
    }
    
    if (!demoWindow || demoWindow.closed) {
      var spot = placeDemoWindow();
      demoWindow = window.open(target, "", 
      "scrollbars=no,status=no,resizeable=yes,title-bar=no,menubar=false,toolbar=false,location=false,personalbar=false,status=false"
       + ",height=300,width=300"
       + ",left=" + spot.x + ",top=" + spot.y);
      if (demoWindow == null) {
        $log.warn("pop-up blocked");
      }
    }
    
    if (demoWindow && !demoWindow.closed) {    
      demoWindow.focus();
      demoWindow.location = target;
    }
  }
  
  
  function tryIt() {
    $log.log("tryIt!");
    showDemoWindow("fieldExample.html", "Live Object Sync")
    settings.fieldExample.number_(7);
    return false;
  }
  
  var connection = $sync.connect("http://localhost:8080/test/sync", 
    {authorization:"guest", appVersion:"0.1", connected:subscribe});
  
});
