$(function() {
  $('#showDemoWindow').click(LiquidDemo.showDemoWindow);
  
  subscribe(function(settings) {

    $('#fieldDemo').click(function() {
      settings.currentDemo.string_("none");
      return false;
    });    
    
//    $('#userName').dataTextField(settings.user.firstName);
//    
//    $('#reminders').dataSortable({model: settings.reminders, render: drawReminder});
    
  });
});

