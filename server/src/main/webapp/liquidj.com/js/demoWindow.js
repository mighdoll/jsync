
var LiquidDemo = LiquidDemo || {};

/** Open a separate browser window to show the demo results.  
 * (Reuses the window if it's already been opened.)  */
var scopeWrapper = function() {
  var demoWindow;

  LiquidDemo.showDemoWindow = function() {
    showSideWindow("demoResults.html");
  };
  
  function showSideWindow(target) {
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
    } else {
      demoWindow.location = target;
    }
  };

}();