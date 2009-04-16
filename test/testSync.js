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

testSync.test1 = function() {};

test("createSyncable", function() {
    var model = {name:"emmett"};    
    var obj = $sync.manager.createSyncable("testSync.test1", model);

    ok(obj.id);
    ok(obj.kind === "testSync.test1");
    ok(obj.toString !== Object.toString);
    ok(typeof obj.name_ === "function");
    obj.name_("milo");
    ok(obj.name === "milo");
    
    $sync.manager.reset(); });

test("createSyncable.noKind", function() {
    var model = {name:"jackie"};
    var obj = $sync.manager.createSyncable(null, model);

    ok(obj.id);
    ok(obj.kind);
    ok(typeof obj.name_ === "function");
    obj.name_("milo");
    ok(obj.name === "milo");

    $sync.manager.reset(); });

test("$sync.manager.update", function() {
    var obj, model = {data:"bar"};
    
    obj = $sync.manager.createSyncable(null, model, {$syncId:10});
    $sync.manager.update({id:obj.id, data:"foo"});
    ok(obj.data === "foo");
    obj.data_(22);
    ok(obj.data === 22);
    $sync.manager.reset(); });


test("json-sync.$sync.set", function() {
    var sub, connection, count = 0;

    var init = function() {
        expect(6);
        connection = $sync.connect(null, {
            testMode:true
        });
        sub = connection.subscribe("", function(set) {
            verifySet(set);
            $sync.manager.reset();
        });
        connection.testFeed(
            [{  "#transaction": 1 } ,
            { "id" : sub.id,
              "root" : {"$ref": 1} },
            { "#edit" : 1,
              "edits" : [ {"put" : [2,3]} ]},
            { "id": 1,
              "kind": "$sync.set"},
            { "id" : 2,
               "name" : "fred-1"},
            { "id" : 3,
               "name" : "fred-2"} ]); };

    var verifySet = function(set) {
		// verify sync.set
        ok(set.kind === "$sync.set");
        ok(typeof set.put === "function");
        ok(set.size() === 2);
        set.each(function(item) {
            count += 1;
            ok(item.name === ("fred-"+count)); });
        ok(count === 2); }
    
    init();
});


test("json-sync.$ref", function() {
    var connection, obj4, obj5, obj6;

    var init = function() {
        expect(5);
        connection = $sync.connect(null, {testMode:true });
        sub = connection.subscribe("", function(root) {
            verifyRefs(root);
            $sync.manager.reset(); });
        connection.testFeed(
            [{  "#transaction": 1 } ,
            { "id" : sub.id,
              "root" : {"$ref": 4} },
            { "id" : 4,
              "forwardRef" : {"$ref": 5} },
            { "id" : 5,
               "selfRef" : {"$ref": 5} },
            { "id" : 6,
              "refsArray": [ {"$ref": 5}, {"$ref": 6} ],
                "nestedRef": { "nest" : { "deep" : {"deeper" : {"$ref":4 }}}}}]); };

    var verifyRefs = function(obj4) {
        // verify $ref decoding
        obj5 = $sync.manager.get(5);
        obj6 = $sync.manager.get(6);
        ok(obj4.forwardRef === obj5 );
        ok(obj5.selfRef === obj5 );
        ok(obj6.refsArray[0] === obj5);
        ok(obj6.refsArray[1] === obj6);
        ok(obj6.nestedRef.nest.deep.deeper === obj4);
		
        start(); };

    init();
});

test("json-sync.send-obj", function() {
    var leonardo, subscriptions, connection, model, obj, out;

    connection = $sync.connect(null, {testMode:true });
    model = {name:"Leonardo"};
    obj = $sync.manager.createSyncable(null, model);
    $sync.manager.commit();
    out = connection.testOutput();
    ok(out[0]['#transaction'] === 0);

    leonardo = out.eachCheck(function(elem) {
        if (elem.id === obj.id)
            return elem;
        return null;
    });
    ok(leonardo);
    ok(leonardo.name === model.name);
    ok(leonardo.id === obj.id);
    ok(leonardo.kind === obj.kind);

    subscriptions = out.eachCheck(function(elem) {
        if (elem.id === "#subscriptions")
            return elem
        return null;
    });
    ok(subscriptions);
    $sync.manager.reset();
});

/*
test("sync.linkedList", function() {
    var connection, count = 0, oldHead;
	expect(8);
    connection = $sync.connect("file:test1.json");
    connection.subscribe("list").await(
	function(list) {
		ok(list.kind === "sync.linkedList");
		ok(list.head.id === 7);
		ok(list._size === 3);
		ok(list.size() === 3);
		list.each(function() {
			count++;
		});
		ok(count === 3);
		oldHead = list.head;
		list.remove(list.head);
		ok(list.size() === 2);
		list.insert(oldHead, list.head);
		ok(list.size() === 3);
		ok(list.head.id === 8);
		$sync.manager.reset();
		start();
	});
	connection.start();
});
*/
