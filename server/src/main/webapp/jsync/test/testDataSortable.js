sortableTest('render', function(seq, $div) {
  var $children = $div.children();
  ok($children.size() === 3);
  ok($children.first().html() === 'albert');
  ok($children.last().html() === 'christine');
});

sortableTest('delete', function(seq, $div) {
  seq.removeAt(1);
  var $children = $div.children();
  ok($children.size() === 2);
});

sortableTest('move', function(seq, $div) {
  seq.moveAt(1, 2);
  var $children = $div.children();
  ok($children.last().html() === 'jon');
});

sortableTest('rename', function(seq, $div) {
  seq.getAt(0).name_('ambika');
  
  var $children = $div.children();
  ok($children.first().html() === 'ambika');
});

(function() {
  var usedMap = false;

  var renderMap = {
    '$sync.test.nameObj': function (model) {
        usedMap = true;
        return renderName(model);
      }
  };
  
  sortableTest('renderFnMap', function(seq, $div) {
    var $children = $div.children();
    ok($children.size() === 3);    
    ok(usedMap);
  }, renderMap);
  
})();


sortableTest('ui.move', function(seq, $div) {
  ok(seq.getAt(0).name == 'albert');
  $sync.observation.watch(seq, function(change) {
    console.log('seq: ', change); 
  });
});

function sortableTest(testName, fn, renderFn) {
  loggedTest('dataSortable.' + testName, function() {
    $sync.manager.withPartition('test', function() {
      var seq = $sync.sequence();
      seq.append($sync.test.nameObj({name:"albert"}));
      seq.append($sync.test.nameObj({name:"jon"}));
      seq.append($sync.test.nameObj({name:"christine"}));
      
      var $div = 
        $('<div/>', {
          id:'dataSortable'
//          'class': 'offscreen'
          }).appendTo('body');
      $div.dataSortable({model:seq, render:renderFn ? renderFn : renderName});
      fn(seq, $div);
//      $div.dataSortable('destroy');
//      $div.remove();
    });  
    $sync.manager.reset();
  });
}

function testSortable() {
  return $sync.manager.withDefaultPartition('test', function() {
    return seq;
  });
}


function renderName(named) {
  return $('<div>' + named.name + '</div>');  // can, but needn't be a jquery object
}

