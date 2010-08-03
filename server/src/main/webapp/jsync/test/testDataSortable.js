sortableTest('ui.move', function(seq, $div) {
  ok(seq.getAt(0).name == 'albert');
  var $first = $div.children().first();
  var $second = $first.next();
  simulateDrag($first, $second);
  ok(seq.getAt(0).name == 'jon');
});


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

sortableTest('ui.remove', function(seq, $div) {
  withTempWidget('sortable2', 'sortable', {}, function($div2) {
    $div2.css({
      minHeight:'10px',
      border: 'solid red 2px'
    });
    $div.sortable("option", "connectWith", '#sortable2');
    
    // drag first element to a linked sortable
    var $first = $div.children().first();  
    simulateDrag($first, $div2);
    ok(seq.getAt(0).name === 'jon');
    ok(seq.size() === 2);
  });  
});

/** simulate a drag between jquery elements */
function simulateDrag($start, $end) {
  var from = eventPosition($start);
  var to = eventPosition($end);
  $start.simulate('mousedown', from);       
  $(document).simulate('mousemove', to);
  $end.simulate('mouseup', to);
  
  // JS - why doesn't .simulate work if we send all the events to document (instead of just mousemove)?  
}

/** return a {clientX,clientY} for the lower right quadrant of the given jquery DOM element */
function eventPosition($spot) {
  var offset = $spot.offset();
  return {
    clientX: offset.left + Math.round(($spot.width() * 2 / 3)),
    clientY: offset.top + Math.round(($spot.height() * 2 / 3)) - $(window).scrollTop()
  };
}

/** insert a div into the ui test area and return it */
function insertDiv(id) {
  return $('<div/>', {id:id}).appendTo(testBox());
}


/** run a function with a temporary widget inserted into the ui test area */
function withTempWidget(id, widgetFn, opts, fn) {
  var $div = insertDiv(id);  
  $div[widgetFn](opts);
  fn($div);
  $div.remove();
  $div[widgetFn]('destroy');
}

function testBox() {
  if ($('#testBox').size() == 0) {
    var $div = $('<div/>', {id:'testBox'}).appendTo('body');    
  }
  return $('#testBox');
}

function sortableTest(testName, fn, renderFn) {
  loggedTest('dataSortable.' + testName, function() {
    $sync.manager.withPartition('test', function() {
      var seq = $sync.sequence();
      seq.append($sync.test.nameObj({name:"albert"}));
      seq.append($sync.test.nameObj({name:"jon"}));
      seq.append($sync.test.nameObj({name:"christine"}));
      
      withTempWidget('dataSortable1', 'dataSortable', 
          {model:seq, render:renderFn ? renderFn : renderName}, 
          function($div) {
        fn(seq, $div);
      });
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

