test("dataContents.toHtml", function() {
  expect(1);
  var model = $sync.manager.withPartition("test", function() {
    return $sync.syncString({string:"sloobar"});
  });
  
  $("#dataContents").dataContents(model);
  ok($("#dataContents").html() === "sloobar");
});