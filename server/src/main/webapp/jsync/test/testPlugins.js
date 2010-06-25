// render by manually watching property changes
test("propertyToHTML", function() {
  nameRenderTest(function(performer) {
    $("#dataContents").html(performer.name);
    return performer.nameChanges().watch(function(change) { // return so we can .destroy()
      $("#dataContents").html(performer.name);
    });
  }).destroy();
});

// render via dataContents plugin, watching a property
test("dataContents.property", function() {
  nameRenderTest(function(performer) {
    $("#dataContents").dataContents({data : performer.nameChanges()});
  });
  $("#dataContents").dataContents('destroy');
});

// render via dataContents plugin, via custom render fn
test("dataContents.renderFn", function() {
  nameRenderTest(function(performer) {
    $("#dataContents").dataContents( {
      data : performer.nameChanges(),
      render : function(obj, change) {
        return '<div>' + obj[change.property] + '</div>';
      }
    });
  }, "<div>Goofy</div>", "<div>Donald</div>");
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

// support function that runs the tests
function nameRenderTest(fn, expected1, expected2) {
  expect(2);
  var performer = $sync.test.nameObj({name : "Goofy"}, {partition : "test"});
  var result = fn(performer);
  ok($("#dataContents").html() === (expected1 || "Goofy"));
  performer.name_("Donald");
  ok($("#dataContents").html() === (expected2 || "Donald"));
  return result;
}
