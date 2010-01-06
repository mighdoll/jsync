$(document).ready(function() {
  var connection;
  
  function reset() {
    $('.status .connected').hide();
  }
  
  function connectToService() {
    $sync.manager.autoCommit();
    connection = $sync.connect("/admin/sync", {
      connected: onConnect
    });
  }
  
  function onConnect() {
    $('.status .connected').show();
    registerControls();
  }
  
  function registerControls() {
    $('#simpleDbQuery form').submit($sync.serviceForm(displayObjects));
  }  
  
  function displayObjects() {
    $log.log("displayObjects");
  }
  
  reset();
  connectToService();  
});
