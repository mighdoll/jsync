$(function() {
  subscribe(function(settings) {
    $("#currentDemo").dataContents({data:settings.currentDemoChanges()});
  });
});
