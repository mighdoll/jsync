loggedTest("dataSortable.render", function() {
  $sync.manager.setDefaultPartition('test');
  var seq = $sync.sequence();
  seq.append($sync.test.nameObj({name:"albert"}));
  seq.append($sync.test.nameObj({name:"jon"}));
  seq.append($sync.test.nameObj({name:"christine"}));
  var $div = 
    $('<div/>', {
      id:'dataSortable'
//      'class': 'offscreen'
      }).appendTo('body');
  $div.dataSortable({model:seq, render:renderName});
  ok($div.children().size() === 3);
  ok($div.children().first().html() === 'albert');
  ok($div.children().last().html() === 'christine');
});

function renderName(named) {
  return '<div>' + named.name + '</div>';  
}