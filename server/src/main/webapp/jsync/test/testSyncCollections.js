/*   Copyright [2009] Digiting Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
// TODO test sequence.each
// TODO test that elements in sequence are sorted


function testCollectionClass(className, creator) {
  test(className + ".put", function() {
    $sync.manager.setDefaultPartition("test");
    var set = creator();

    expect(8);

    var item1 = $sync.test.nameObj();
    var item2 = $sync.test.nameObj();

    ok(set.size() == 0);
    ok(set.contains(item1) == 0);

    set.put(item1);
    ok(set.size() == 1);
    ok(set.contains(item1));

    set.put(item1);
    ok(set.size() == 1);

    set.put(item2);
    ok(set.size() == 2);
    ok(set.contains(item1));
    ok(set.contains(item2));
  });

  test(className + ".remove", function() {
    $sync.manager.setDefaultPartition("test");
    var set = creator();
    var item1 = $sync.test.nameObj();
    var item2 = $sync.test.nameObj();

    expect(3);

    set.put(item1);
    set.put(item2);
    set.remove(item1);

    ok(set.size() == 1);
    ok(!set.contains(item1));
    ok(set.contains(item2));

    $sync.manager.reset();
  });

  test(className + ".clear", function() {
    $sync.manager.setDefaultPartition("test");
    var set = creator();
    var item1 = $sync.test.nameObj();

    expect(2);

    set.put(item1);
    set.clear();
    ok(set.size() == 0);
    ok(!set.contains(item1));

    $sync.manager.reset();
  });

  test(className + ".notification1", function() {
    $sync.manager.setDefaultPartition("test");
    var set = creator();
    var item1 = $sync.test.nameObj();

    expect(7);
    set.put(item1);
    withWatch(set, function(change) {
      ok(change.target === set);
      ok(change.changeType == 'edit');
	  if (set.kind == "$sync.set") {
	  	ok(change.remove === item1);	
	  } else if (set.kind == "$sync.sequence") {
	  	ok(change.removeAt === 0);
	  } else {
	    $debug.error("unexpected collection type in test: " + className + ".notification");	  	
	  }
    })(function() {
      set.remove(item1);
    });
    set.put(item1);
    withWatch(set, function(change) {
      ok(change.target === set);
      ok(change.changeType == 'edit');
      ok(!('put' in change));
      ok(change.clear === true);
    })(function() {
      set.clear();
    });

    $sync.manager.reset();
  });
}

testCollectionClass("set", $sync.set);
testCollectionClass("sequence", $sync.sequence);

test("set.notification2", function() {
  $sync.manager.setDefaultPartition("test");
  var set = $sync.set();
  var item1 = $sync.test.nameObj();
  
  withWatch(set, function(change) {
    ok(change.target === set);
    ok(change.changeType == 'edit');
    ok(change.put === item1);
  })(function() {
    set.put(item1);
  });
});

test("sequence.notification2", function() {
  $sync.manager.setDefaultPartition("test");
  var set = $sync.sequence();
  var item1 = $sync.test.nameObj();
  
  withWatch(set, function(change) {
    ok(change.target === set);
    ok(change.changeType == 'edit');
    ok(change.insertAt.elem === item1);
  })(function() {
    set.put(item1);
  });
});

test("sequence.indexOf", function() {
  $sync.manager.setDefaultPartition("test");
  var seq = $sync.sequence();
  var item1 = $sync.test.nameObj();
  var item2 = $sync.test.nameObj();
  seq.put(item1);
  seq.put(item2);
  ok(seq.indexOf(item1) === 0);
  ok(seq.indexOf(item2) === 1);
});

test("sequence.insert", function() {
  $sync.manager.setDefaultPartition("test");
  var seq = $sync.sequence();
  var item1 = $sync.test.nameObj();
  var item2 = $sync.test.nameObj();
  var item3 = $sync.test.nameObj();
  seq.put(item1);
  seq.put(item2);
  seq.insert(item3, item1);
  ok(seq.size() == 3);
  matchSequenceItems(seq, [item1, item3, item2]);
});

test("sequence.move", function() {
  $sync.manager.setDefaultPartition("test");
  var seq = $sync.sequence();
  var item1 = $sync.test.nameObj();
  var item2 = $sync.test.nameObj();
  var item3 = $sync.test.nameObj();
  seq.put(item1);
  seq.put(item2);
  seq.put(item3);
  withWatch(seq, function(change) {
    ok(change.changeType == 'edit');
    ok(change.move.from === 2);
    ok(change.move.to === 1);
  })(function() {
    seq.move(item3, 1);
  });
  ok(seq.size() == 3);
  matchSequenceItems(seq, [item1, item3, item2]);
  withWatch(seq, function(change) {
    ok(change.changeType == 'edit');
    ok(change.move.from === 2);
    ok(change.move.to == 0);
  })(function() {
    seq.move(item2);
  });
  ok(seq.size() == 3);
  matchSequenceItems(seq, [item2, item1, item3]);
});

test("sequence.each", function() {
  $sync.manager.setDefaultPartition("test");
  var seq = $sync.sequence();
  var item1 = $sync.test.nameObj();
  var item2 = $sync.test.nameObj();
  seq.put(item1);
  seq.put(item2);
  var i_expect = 0;
  var items_expect = [item1, item2];
  seq.each(function(item, i) {
    ok(i === i_expect);
    i_expect++;
    ok(item == items_expect[i]);
  })
});

/** verify a sequence matches an array
 * undefined elements in the match array are skipped. */
function matchSequenceItems(seq, matchArray) {
  var i, match;
  for (i = 0; i < matchArray.length; i++) {
  	match = matchArray[i];
	if (match !== undefined) {
	  ok(match === seq.getAt(i));
	}
  }
}

// usage:
//   withWatch(collection, fn, owner)(function() {body})
// is equivalent to:
//   $sync.observation.watch...
//   body
//   $sync.observation.ignore
function withWatch(collection, fn, owner) {
  $sync.observation.watch(collection, fn, owner);
  return function(body) {
    var value = body();
    // TODO use try/except, in MSIE-safe way
    $sync.observation.ignore(collection, fn, owner);
    return value;
  }
}
