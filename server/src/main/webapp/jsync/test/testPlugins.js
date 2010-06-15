// render via manual wiring of property to
test("propertyToHTML", function() {
  nameRenderTest(function(performer) {
    $("#dataContents").html(performer.name);
    return performer.nameChanges().watch(function(change) {
      $("#dataContents").html(performer.name);
    });
  }).destroy();
});

// render via dataContents plugin, watching a property
test("dataContents.property", function() {
  nameRenderTest(function(performer) {
    $("#dataContents").dataContents({data:performer.nameChanges()});
  });
  $("#dataContents").dataContents('destroy');
});

// //render via dataContents plugin and toHTML() in the model object
// test("dataContents.toHtml", function() {
// nameRenderTest(function(performer) {
// perfomer.toHtml = function() { return this.name;} ;
// $("#dataContents").dataContents(performer);
// });
// });
//
//
// // render via dataContents plugin, watching $all and using provided render
// function
// test("dataContents.$all", function() {
// function render(obj) {
// return "<div>" + obj.name + "</div";
// };
// nameRenderTest(function(performer) {
// $("#dataContents").dataContents(performer.$allChanged, {render:render});
// }, "<div>Donald</div>");
// $("dataContents").
// });

//support function that runs the tests
function nameRenderTest(fn, expected) {
  expect(2);
  var performer = $sync.test.nameObj( {
    name : "Goofy"
  }, {partition:"test"});
  var result = fn(performer);
  ok($("#dataContents").html() === "Goofy");
  performer.name_("Donald");
  ok($("#dataContents").html() === (expected || "Donald"));
  return result;
}


