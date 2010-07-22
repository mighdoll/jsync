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
test("observe.watch", function() { 
  $sync.manager.setDefaultPartition("test");
  var syncable = $sync.test.valueObj({value:10});
  
  expect(4);
  
  function changed(changes) {
    ok(changes.oldValue === 10);
    ok(changes.property === "value");
    ok(syncable.value === 21);
  }
  
  $sync.observation.watch(syncable, changed);
  syncable.value_(21);
  
  $sync.observation.ignore(syncable, changed); // so if we call changed() again, expect() will fail
  syncable.value_(2);
  ok(syncable.value === 2);
  
  $sync.manager.reset();
});

test("observe.ignore", function() {
  $sync.manager.setDefaultPartition("test");
  var syncable = $sync.test.valueObj({value:10});
  
  expect(4);
  
  function changed(changes) {
    ok(changes.oldValue === 10);
    ok(changes.property === "value");
    ok(syncable.value === 21);
  }
  
  $sync.observation.watch(syncable, changed, "changeTest");
  syncable.value_(21);
  
  $sync.observation.ignore(syncable, "changeTest"); // if we call changed() again expect() will be wrong
  syncable.value_(2);
  ok(syncable.value === 2);
  
  $sync.manager.reset();
});

test("observe.pause", function() {
  var fired = false;  
  $sync.manager.setDefaultPartition("test");
  var syncable = $sync.test.valueObj({value:"fred"});
  expect(2);
  
  $sync.observation.watch(syncable, function() {
    fired = true;
  });
  
  $sync.observation.pause(function() {
    syncable.value_("sam");
    ok(fired === false);
  });
  ok(fired === true);
  $sync.manager.reset();
});


test("observe.watch.set", function() {
  $sync.manager.setDefaultPartition("test");
  var syncable = $sync.test.valueObj({value:"jim"});
  var set = $sync.set();
  var results = [];
  expect(5);
  
  function changed(changes) {
    results.push(changes);
  };
  
  $sync.observation.watch(set, changed);
  set.put(syncable);
  set.remove(syncable);
  ok(results.length === 2);
  ok(results[0].put === syncable);
  ok(!results[0].remove);
  ok(results[1].remove === syncable);
  ok(!results[1].put);
  
  $sync.manager.reset();
});

test("observe.watchEvery", function() {
  var syncable;
  var results = [];
  
  $sync.manager.setDefaultPartition("test");
  $sync.observation.watchEvery(function(changes) {
    results.push({
      obj: changes.target,
      changes: changes
    });
  });
  
  syncable = $sync.test.valueObj({value:"bruce"});
  syncable.value_("milo");
  
  ok(results.length === 2);
  ok(results[0].obj === syncable);
  ok(results[0].changes.changeType === "create");
  ok(results[1].obj === syncable);
  ok(results[1].changes.changeType === "property");
  ok(results[1].changes.property === "value");
  ok(results[1].changes.oldValue === "bruce");
});

test("observe.set.watchEvery", function() {
  var set, elem;
  var results = [];
  
  $sync.manager.setDefaultPartition("test");
  $sync.observation.watchEvery(function(changes) {
    results.push({
      obj: changes.target,
      changes: changes
    });
  });
  
  set = $sync.set();
  elem = $sync.test.nameObj({name:"sven"});  
  set.put(elem);
  
  ok(results.length === 3);
  ok(results[0].obj === set);
  ok(results[0].changes.changeType === "create");
  ok(results[1].obj === elem);
  ok(results[1].changes.changeType === "create");
  ok(results[2].obj === set);
  ok(results[2].changes.changeType === "edit");
  ok(results[2].changes.put === elem);
});

test("observe.property", function() {  
  expect(4);
  var nameObj = $sync.manager.withPartition("test", function() {
    return $sync.test.nameObj({name:"fred"});
  });
  nameObj.nameChanges().watch(verify);
  nameObj.$allChanges().watch(verify);
  nameObj.name_("barney");
  
  function verify(change) {
    if (change.changeType === 'initial') {
      ok(nameObj.name === 'fred');
    }
    if (change.changeType == 'property') {
      ok(nameObj.name === "barney");
    }
  }
});


