$(function() {
  var settings, demoWindow;  
  var connection = $sync.connect("http://localhost:8080/test/sync", 
      {authorization:"guest", appVersion:"0.1", connected:subscribe});
    
  function subscribe() {
    registerButtons();
    connection.subscribe("GuestSettings", "guests", registerButtons);
  }
  function registerButtons() {
    $('#tryIt').click(function() {
      LiquidDemo.showDemoWindow("fieldExample.html");
      $log.log("try modifyObject!");      
      modifyObject();      
      return false;
    });
  }
  function modifyObject() {
    settings.fieldExample.number_(7);
  }
  
});
