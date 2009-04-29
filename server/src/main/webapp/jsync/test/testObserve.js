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
    var syncable = $sync.manager.createSyncable(null, {value:10});
	
    expect(4);
	
    function changed(ignoring, changes) {
        ok(changes.oldValue === 10);
        ok(changes.property === "value");
        ok(syncable.value === 21);
    }
	
    $sync.notification.watch(syncable, changed);
    syncable.value_(21);
	
    $sync.notification.ignore(syncable, changed);	// if we call changed() again expect() will be wrong
    syncable.value_(2);
    ok(syncable.value === 2);
	
    $sync.manager.reset();
});

test("observe.ignore", function() {
    var syncable = $sync.manager.createSyncable(null, {value:10});
	
    expect(4);
	
    function changed(ignoring, changes) {
        ok(changes.oldValue === 10);
        ok(changes.property === "value");
        ok(syncable.value === 21);
    }
	
    $sync.notification.watch(syncable, changed, "changeTest");
    syncable.value_(21);
	
    $sync.notification.ignore(syncable, "changeTest");	// if we call changed() again expect() will be wrong
    syncable.value_(2);
    ok(syncable.value === 2);
	
    $sync.manager.reset();
});

test("observe.pause", function() {
    var fired = false, 
    syncable = $sync.manager.createSyncable(null, {value:"fred"});
    expect(2);
	
    $sync.notification.watch(syncable, function() {
        fired = true
        });
	
    $sync.notification.pause();
    syncable.value_("sam");
    ok(fired === false);
    $sync.notification.endPause();
    ok(fired === true);
	
    $sync.manager.reset();
});


test("observe.watch.set", function() {
    var syncable = $sync.manager.createSyncable(null, {value:"jim"}),
    set = $sync.set(), results = [];
    expect(5);
	
    var changed = function(collection, changes) {
        results.push(changes);
    };
	
    $sync.notification.collectionWatch(set, changed);
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

    $sync.notification.watchEvery(function(obj, changes) {
        results.push({obj:obj, changes:changes});
    });

    syncable = $sync.manager.createSyncable(null, {value:"bruce"});
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

    $sync.notification.watchEvery(function(obj, changes) {
        results.push({obj:obj, changes:changes});
    });

    set = $sync.set();
    elem = $sync.manager.createSyncable(null, {name:"sven"});
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


