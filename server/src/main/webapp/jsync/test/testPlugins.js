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

function renderInDiv(performer) {
  return '<div>' + performer.name + '</div>';
}

// render via dataContents plugin, via custom renderFn
test("dataContents.renderFn", function() {  
  nameRenderTest(function(performer) {
    $("#dataContents").dataContents( {
      data : performer.nameChanges(),
      render : renderInDiv
    });
  }, "<div>Goofy</div>", "<div>Donald</div>");
  $("#dataContents").dataContents('destroy');
});

// render via dataContents plugin, via renderFn map
test("dataContents.renderFnMap", function() {
  nameRenderTest(function(performer) {
    var renderMap = {string:renderInDiv};
    $("#dataContents").dataContents( {
      data : performer.nameChanges(),
      render : renderMap
    });
  }, "<div>Goofy</div>", "<div>Donald</div>");
  $("#dataContents").dataContents('destroy');
});

//render via dataContents plugin, via renderFn map
test("dataContents.$all", function() {
  nameRenderTest(function(performer) {
    $("#dataContents").dataContents( {
      data : performer.$allChanges(),
      render : renderInDiv
    });
  }, "<div>Goofy</div>", "<div>Donald</div>");
  $("#dataContents").dataContents('destroy');
});


// support function that runs the tests
function nameRenderTest(fn, expected1, expected2) {
  expect(2);
  var performer = $sync.test.nameObj({name : "Goofy"}, {partition : "test"});
  var result = fn(performer);
  ok($("#dataContents").html() === (expected1 || "Goofy"));
  performer.name_("Donald");
  ok($("#dataContents").html() === (expected2 || "Donald"));
  $sync.manager.reset();
  return result;
}
