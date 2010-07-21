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
var testSync = testSync || {};

test("createSyncable", function() {
  $sync.manager.setDefaultPartition("test");
  var obj = $sync.test.nameObj();
  
  ok(obj.$id);
  ok(obj.$kind === "$sync.test.nameObj");
  ok(obj.toString !== Object.toString);
  ok(typeof obj.name_ === "function");
  obj.name_("milo");
  ok(obj.name === "milo");
  
  $sync.manager.reset();
});

test("$sync.update.updateInstance", function() {
  $sync.manager.setDefaultPartition("test");
  var obj;
  
  $sync.manager.withNewIdentity({
    partition: "test",
    $id: 10
  }, function() {
    obj = $sync.test.nameObj({
      name: "bar"
    });
  });
  
  ok(obj.name === "bar");
  
  $sync.observation.withMutator("server", function() {
    $sync.update.updateInstance ({
      $id: obj.$id,
      $partition: "test",
      name: "foo"
    });
  });
  ok(obj.name === "foo");
  obj.name_("fred");
  ok(obj.name === "fred");
  $sync.manager.reset();
});


test("json-sync.$sync.set", function() {
  $sync.manager.setDefaultPartition("test");
  var sub, connection;
  
  function init() {
    expect(6);
    $sync.manager.setDefaultPartition("test");
    connection = $sync.connect(null, {
      testMode: true
    });
    sub = connection.subscribe("", "test", function(set) {
      verifySet(set);
      $sync.manager.reset();
    });
    connection.testReceiveMessages([[{
      "#transaction": 0
    }, {
      "$id": sub.$id,
      "$partition": sub.$partition,
      "root": {
        "$ref": {
          $id: 1,
          $partition: "test"
        }
      }
    }, {
      "#edit": {
        $id: 1,
        $partition: "test"
      },
      "put": [{
        $id: 2,
        $partition: "test"
      }, {
        $id: 3,
        $partition: "test"
      }]
    }, {
      "$id": 1,
      "$partition": "test",
      "$kind": "$sync.set"
    }, {
      "$id": 2,
      "$partition": "test",
      "$kind": "$sync.test.nameObj",
      "name": "fred-1"
    }, {
      "$id": 3,
      "$partition": "test",
      "$kind": "$sync.test.nameObj",
      "name": "fred-2"
    }]]);
  };
  
  function verifySet(set) {
    var count = 0;
    // verify sync.set
    ok(set.$kind === "$sync.set");
    ok(typeof set.put === "function");
    ok(set.size() === 2);
    set.each(function(item) {
      count += 1;
      ok(item.name === ("fred-" + count));
    });
    ok(count === 2);
  }
  
  init();
});


test("json-sync.$ref", function() {
  var connection, subscription;
  function begin() {
    $sync.manager.setDefaultPartition("test");
    connection = $sync.connect(null, {
      testMode: true
    });
    subscription = connection.subscribe("", "test", function(root) {
      verifyRefs(root);
    });
    
    // synthesize response from server
    connection.testReceiveMessages([[{
      "#transaction": 0
    }, {
      "$id": subscription.$id,
      "$partition": subscription.$partition,
      "root": {
        "$ref": {
          $partition: "test",
          $id: 4
        }
      }
    }, {
      "$id": 4,
      "$partition": "test",
      "$kind": "$sync.test.refObj",
      "ref": { // forward ref
        "$ref": {
          $partition: "test",
          $id: 5
        }
      }
    }, {
      "$id": 5,
      "$partition": "test",
      "$kind": "$sync.test.refObj",
      "ref": { // self ref
        "$ref": {
          $partition: "test",
          $id: 5
        }
      }
    }, {
      "$id": 6,
      "$partition": "test",
      "$kind": "$sync.test.refObj",
      "ref": [{ // array of refs
        "$ref": {
          $partition: "test",
          $id: 5
        }
      }, {
        "$ref": {
          $partition: "test",
          $id: 7
        }
      }]
    }, {
      "$id": 7,
      "$partition": "test",
      "$kind": "$sync.test.refObj",
      "ref": { // reference syncable via non-syncable reference chain
        "nest": {
          "deep": {
            "deeper": {
              "$ref": {
                $partition: "test",
                $id: 4
              }
            }
          }
        }
      }
    }]]);
    stop();
  }
  
  function verifyRefs(obj4) {
    var obj4, obj5, obj6;
    
    // verify $ref decoding
    obj4 = subscription.root;
    obj5 = $sync.manager.get("test", 5);
    obj6 = $sync.manager.get("test", 6);
    obj7 = $sync.manager.get("test", 7);
    ok(obj4.ref === obj5);
    ok(obj5.ref === obj5);
    ok(obj6.ref[0] === obj5);
    ok(obj6.ref[1] === obj7);
    ok(obj7.ref.nest.deep.deeper === obj4);
    
    testComplete();
  }
  
  expect(5);
  begin();
});

test("json-sync.send-obj", function() {
  $sync.manager.setDefaultPartition("test");
  var leonardo, subscriptions, connection, obj, out;
  
  connection = $sync.connect(null, {
    testMode: true
  });
  obj = $sync.test.nameObj({
    name: "Leonardo"
  });
  $sync.manager.commit();
  out = connection.testOutput();
  ok(out[0]['#transaction'] === 0);
  
  $.each(out, function(index, elem) {
    if (elem.$id === obj.$id) {
      leonardo = elem;
      return false;
    }
  });
  
  ok(leonardo);
  ok(leonardo.name === obj.name);
  ok(leonardo.$id === obj.$id);
  ok(leonardo.$kind === obj.$kind);
  
  subscriptions = $sync.util.arrayFind(out, function(elem) {
    if (elem.$id === "subscriptions") 
      return elem;
    return undefined;
  });
  ok(subscriptions === undefined);
  $sync.manager.reset();
});

test("json-sync.receive-out-of-order", function() {
  $sync.manager.setDefaultPartition("test");
  
  connection = $sync.connect(null, {
    testMode: true
  });
  
  connection.testReceiveMessages([[{
    "#transaction": 1
  }, {
    $id: "test-1",
    $partition: "test",
    $kind: "$sync.test.nameObj",
    name: "oliver"
  }]]);
  
  testObj = $sync.manager.get("test", "test-1");
  ok(testObj === undefined); // shouldn't have procesed #1 yet'
  
  connection.testReceiveMessages([[{
    "#transaction": 0
  }]]);
  testObj = $sync.manager.get("test", "test-1");
  ok(testObj && testObj.name === "oliver");
  
  $sync.manager.reset();
});

