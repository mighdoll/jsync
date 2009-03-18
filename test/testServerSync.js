/*
var connection = $sync.connect("http://localhost:8080/sync", {connected:gotit});

function gotit() {
    console.log("connected");
}
connection.subscribe("$sync/test/oneName", function(oneName) {
   ok(oneName.name === "emmett");
   start();
   $sync.manager.reset();
});
$sync.manager.commit();
*/

test("sync.connect", function() {
   expect(1);
   var connected = function() {
       console.log("test-connected");
       ok(connection.isConnected());
       $sync.manager.reset();
       start();
   };

   var connection = $sync.connect("http://localhost:8080/sync", {connected:connected});
   stop();
});

test("sync.subscribe.oneName", function() {
   expect(1);
   
   var connection = $sync.connect("http://localhost:8080/sync");
   connection.subscribe("$sync/test/oneName", function(oneName) {
       ok(oneName.name === "emmett");
       $sync.manager.reset();
       start();
   });
   $sync.manager.commit();

   stop();
});

test("final", function() {});

