
$(function() {
  $('#showDemoWindow').click(LiquidDemo.showDemoWindow);
});

$(function() {
  subscribe(function(settings) {

    $('#fieldDemo').click(function() {
      settings.currentDemo.string_(7);
      return false;
    });    
    
//    $('#userName').dataTextField(settings.user.firstName);
//    
//    $('#reminders').dataSortable({model: settings.reminders, render: drawReminder});
    
  });
});

function subscribe(settingsFn) {
  var connection = $sync.connect("http://localhost:8080/demo/sync", 
      {authorization:"guest", connected:subscribe});

  function subscribe() {
    connection.subscribe("settings", "demos", settingsFn);
  }
}
