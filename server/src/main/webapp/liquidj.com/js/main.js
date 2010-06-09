$(function() {
  var settings, demoWindow;  
  var connection = $sync.connect("http://localhost:8080/demo/sync", 
      {authorization:"guest", connected:subscribe});
    
  function subscribe() {
    connection.subscribe("settings", "demos", function(receivedSettings) {
      settings = receivedSettings;
      registerButtons();
    });
  }
  
  function registerButtons() {
    $('#tryIt').click(function() {
      LiquidDemo.showDemoWindow("fieldExample.html");
      modifyObject();
      return false;
    });
  }
  
  function modifyObject() {
    settings.fieldDemo.number_(7);
  }
  
});
