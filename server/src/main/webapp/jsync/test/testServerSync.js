var $sync = $sync || {};
$sync.test = $sync.test || {};


/** connects to the server */
test("sync.connect", function() {
  var connection;
  function begin() {
    connection = $sync.connect("http://localhost:8080/sync/test", {
      connected: connected
    });
    stop();
  }
  
  function connected() {
    console.log("test-connected");
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
  
  function begin() {
    connection = $sync.connect("http://localhost:8080/sync/test", {
      connected: connected
    });
    stop();
  }
  
  function connected() {
    connection.subscribe("oneName", "test", function(oneName) {
      ok(oneName.name === "emmett");
      $sync.manager.reset();
      start();
    });
    $sync.manager.commit();
  }
  
  expect(1);
  begin();
});

/** downloads a set from the server */
test("sync.subscribe.oneSet", function() {
  var connection;
  var foundMercer = false;

  function begin() {
    connection = $sync.connect("http://localhost:8080/sync/test", {
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
  var connection, connection2;
  
  function begin() {
    connection = $sync.connect("http://localhost:8080/sync/test", {
      connected: connected
    });
    stop();
  }
  
  function connected() {
    connection.subscribe("modifyOneName", "test", function(named) {
      // modify the name and send it to the server
      named.name_(uniqueName);
      $sync.manager.commit();
	  
	  // clear everything, as if the user has logged out and is logging back in
      $sync.manager.reset();  
      
      // reconnect and see that it's modified
      connection2 = $sync.connect("http://localhost:8080/sync/test", {
        connected: connected2
      });
      
    });
    $sync.manager.commit();
  }
  
  function connected2() {
    connection.subscribe("modifyOneName", "test", function(named2) {
      if (named2.name === uniqueName) {
        ok(named2.name === uniqueName);
        $sync.manager.reset();
        start();
      }
      else {
        // our re-subscription may have our previous modification, so watch until it changes
        $sync.observation.watch(named2, function() {
          ok(named2.name === uniqueName);
          $sync.manager.reset();
          start();
        });
      }
    });
    $sync.manager.commit();
  }
  
  expect(1);
  begin();
  
});


/** subscribe and then modify a server set, server makes modification too, 
 * client reconnects and sees both client and server objects in the set. */
test("sync.bothModifyTwoSet", function() {
  var uniqueName = "time-" + $sync.util.now();
  var connection, connection2;  
  var ourName;
    
  function begin() {
    connection = $sync.connect("http://localhost:8080/sync/test", {
      connected: connected
    });
    stop();
  }
  
  function connected() {
    connection.subscribe("twoSet", "test", function(twoSet) {
      $sync.manager.setDefaultPartition("test");
	  ourName = $sync.test.named();
	  ourName.name_(uniqueName);	
	  	
      // modify the name and send it to the server
	  twoSet.clear();
      twoSet.put(ourName);
      $sync.manager.commit();
	  
	  // clear everything, as if the user has logged out and is logging back in
	  connection.close();
      $sync.manager.reset();  
      
      // reconnect and see that it's modified
      connection2 = $sync.connect("http://localhost:8080/sync/test", {
        connected: connected2
      });
      
    });
    $sync.manager.commit();	// commit the subscription
  }
  
  function connected2() {
    connection.subscribe("twoSet", "test", function(twoSet) {
      if (twoSet.size() > 1) {
	  	verify(twoSet);
		finish();
      }
      else {
        // our subscription may have outraced our modification, so watch until it changes
        $sync.observation.watch(twoSet, function() {
		  verify(twoSet);
		  finish();
        });
      }
    });
    $sync.manager.commit();	// commit the subscription
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
  
  function finish() {
    $sync.manager.reset();
    start();  	
  }
  
  expect(3);
  begin();  
});

