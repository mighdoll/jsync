var $sync = $sync || {};
$sync.test = $sync.test || {};


/** connects to the server */
test("sync.connect", function() {
  var connection;
  function begin() {
    connection = $sync.connect("/sync", {
      connected: connected
    });
    stop();
  }
  
  function connected() {
    ok(connection.isConnected());
    $sync.manager.reset();
    start();
  };
  
  expect(1);
  begin();
});

/** downloads a simple object from the server */
test("sync.subscribe.oneName", function() {
  var connection;
  
  expect(1);
  withTestSubscription("oneName", function(oneName) {
    ok(oneName.name === "emmett");
  });
  
});

/** downloads a set from the server */
test("sync.subscribe.oneSet", function() {
  var connection;
  var foundMercer = false;

  function begin() {
    connection = $sync.connect("/sync", {
      connected: connected
    });
    stop();
  }
  function connected() {
    connection.subscribe("oneSet", "test", function(set) {
      ok(set.size() === 1);
      set.each(function(elem) {
        if (elem.name === "mercer") 
          foundMercer = true;
      });
      ok(foundMercer)
      $sync.manager.reset();
      start();
    });
    $sync.manager.commit();
  }
  
  expect(2);
  begin();
});

/**
 * Downloads an object from the server, modifies it locally, 
 * then subscribes from a separate connection to see that it's changed
 */
test("sync.modifyOneName", function() {
  var uniqueName = "time-" + $sync.util.now();

  expect(1);
  withTestSubscription("modifyOneName", modify, undefined, verify);
  
  function modify(named) {
    // modify the name and send it to the server
    named.name_(uniqueName);
  }
  
  function verify(named2) {
	ok(named2.name === uniqueName);
  }
});


/** subscribe and then modify a server set, server makes modification too, 
 * client sees both client and server objects in the set. */
test("sync.bothModifyTwoSet", function() {
  var uniqueName = "time-" + $sync.util.now();
  var connection, connection2;  
  var ourName;
  
  expect(3);
  withTestSubscription("twoSet", modify, undefined, verify);
  
  function modify(twoSet) {
	  ourName = $sync.test.nameObj();
	  ourName.name_(uniqueName);	
	  	
      // modify the name and send it to the server
	  twoSet.clear();
      twoSet.put(ourName);
  }
    
  function verify(twoSet) {
	var foundSelf, foundServerChange;
	
    ok(twoSet.size() === 2);
    twoSet.each(function(elem) {
      if (elem.name === uniqueName) {
        foundSelf = true;
      }
      if (elem.name === ("server-" + uniqueName)) {
    	foundServerChange = true;
      }
    });
    ok(foundSelf);
    ok(foundServerChange);
  }    
});

/** modify an object reference and add a new object containing a reference
 *  on both cliet and server */
test("sync.modifyReference", function() {
  expect(2);
  var newRef;

  withTestSubscription("modifyReference", modify, changed);
  
  function modify(root) {
  	newRef = $sync.test.refObj();
	newRef.ref_(root);
  	root.ref_(newRef);
  }
  
  function changed(changed) {
  	var root = changed.target;
    // verify that server inserts an element
  	ok(root.ref.ref === newRef);
  	ok(root.ref.ref.ref === root);
  }
  
});

