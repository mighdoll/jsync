$(function() {
  var settings;
  
  function subscribe() {
    registerButtons();
//    connection.subscribe("GuestSettings", "guests", registerButtons);
  }
  function registerButtons() {
    $('#tryIt').click(tryIt);
  }
  var demoWindow;
  function showDemoWindow(target) {
    if (!demoWindow || demoWindow.closed) {
      demoWindow = window.open(target, "", 
      "scrollbars=no,status=no,resizble=yes,title-bar=no,left=100,top=100,height=300,width=300,menubar=false,toolbar=false,location=false,personalbar=false,status=false");
      if (demoWindow == null) {
        console.error("pop-up blocked");
      }
    }
    
    if (demoWindow && !demoWindow.closed) {    
      demoWindow.focus();
      demoWindow.location = target;
    }
  }
  
  function tryIt() {
    console.log("tryIt!");
    showDemoWindow("fieldExample.html", "Live Object Sync")
    settings.fieldExample.number_(7);
    return false;
  }
  
  var connection = $sync.connect("http://localhost:8080/test/sync", 
    {authorization:"guest", appVersion:"0.1", connected:subscribe});
  
});
