$(function() {
  subscribe(function(settings) {
    $("#currentDemo").dataContents(settings.currentDemo);
  });
});
