
/** connects to the server */
loggedTest("sync.connect", function() {
  var connection;
  function begin() {
    connection = $sync.connect("/test/sync", {
      connected: connected
    });
    stop();
  }
  
  function connected() {
    ok(connection.isConnected());
    testComplete();
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
loggedTest("sync.subscribe.oneSet", function() {
  var connection;
  var foundMercer = false;

  function begin() {
    connection = $sync.connect("/test/sync", {
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
      ok(foundMercer);
      testComplete();
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
loggedTest("sync.modifyOneName", function() {
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
loggedTest("sync.duplicatingSet", function() {
  var uniqueName = "time-" + $sync.util.now();
  var connection, connection2;  
  var ourName;
  
  expect(3);
  withTestSubscription("duplicatingSet", modify, verify);
  
  function modify(twoSet) {
	  ourName = $sync.test.nameObj();
	  ourName.name_(uniqueName);	
	  	
      // add a name and send it to the server
    twoSet.put(ourName);
  }
    
    // server should have duplicated the change
  function verify(change) {
    var twoSet = change.target;
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
 *  on both client and server */
loggedTest("sync.modifyReference", function() {
  var root, newRef;
  expect(2);

  withTestSubscription("modifyReference", modify, changed);
  
  function modify(serverRoot) {
    root = serverRoot;
  	newRef = $sync.test.refObj();
    newRef.ref_(root);
  	root.ref_(newRef);
    // root -> clientNew -> root
  }
  
  function changed(changed) {
    // verify that server inserts an element
  	ok(root.ref.ref === newRef);
  	ok(root.ref.ref.ref === root);
    // root -> serverNew -> clientNew -> root
  }
  
});

function withProtocolVersion(version, fn) {
  var origVersion = $sync.protocolVersion;
  $sync.protocolVersion = version;
  var result = fn();
  $sync.protocolVersion = origVersion;  
  return result;
}

function withAppVersion(version, fn) {
  var origVersion = $sync.defaultAppVersion;
  $sync.defaultAppVersion = version;
  var result = fn();
  $sync.defaultAppVersion = origVersion;  
  return result;  
}


function testWrongVersion(withVersionFn) {
  expect(2);
  
  withVersionFn("wrong-version", function() {
    $sync.connect("/test/sync", {connected:connected, failFn:failed});  
  });
  
  stop();
  
  function connected() {
    ok(false);
    testComplete();
  }
  
  function failed(conn, ajaxRequest, xmlHttpRequest, textStatus, errorThrown) {
    ok(xmlHttpRequest.status === 400);
    ok(connection.isClosed);
    testComplete();
  }    
}

loggedTest("sync.protocolVersion", function() {
  testWrongVersion(withProtocolVersion);
});

loggedTest("sync.appVersion", function() {
  testWrongVersion(withAppVersion);  
});

// this test isn't reliable, if the browser runs slowly it will fail.
//loggedTest("sync.ajaxTimeout", function() {
//  expect(2);
//  $sync.connect("/test/sync", {
//    requestTimeout : 1, // we want it to timeout
//    connected : connected,
//    failFn : failed
//  });    
//  stop();
//  
//  function connected() {
//    ok(false, "shouldn't have time to connect");
//    testComplete();
//  }
//  
//  function failed(connection, ajaxRequest, xmlHttpRequest, textStatus, errorThrown) {
//    ok(!connection.isClosed);
//    ok(connection.requestsActive() == 0);
//    testComplete();
//  }  
//});

/** send a raw message to the sync test server */
function sendRawTest(msgOrObj, connected, failed) {
  var data;
  
  if (typeof(msgOrObj) === 'string') {
    data = msgOrObj;
  } else {
    data = JSON.stringify(msgOrObj);
  }

  $.ajax({
    url: "/test/sync",
    type: "POST",
    data:data,
    contentType: "application/json",
    success : connected,
    error : failed
  });      
}

loggedTest("sync.oldToken", function() {
  expect(1);  

  var message = [
   {'#transaction':1}, 
   {'#token':"invalid!"} 
  ];
  
  sendRawTest(message, connected, failed);
  stop();
  
  function connected(xmlHttpRequest, textStatus, errorThrown) {
    ok(false);
    testComplete();
  }
  
  function failed(xmlHttpRequest, textStatus, errorThrown) {
    ok(xmlHttpRequest.status === 404, "expected 404");
    testComplete();
  }  
});

loggedTest("sync.primitivesRoundTrip", function() {
  expect(8);

  withTestSubscription("primitivesRoundTrip", startValues, verify);
  
  function startValues(prim) {
    prim.t_(true);
    prim.b_(1);
    prim.s_(2);
    prim.c_('3');
    prim.i_(4);
    prim.l_(5);
    prim.f_(6.0);
    prim.d_(7.0);
  }  
  
  function verify(change) {
    var prim = change.target;
    ok(prim.t === false);
    ok(prim.b === 2);
    ok(prim.s === 3);
    ok(prim.c === '4');
    ok(prim.i === 5);
    ok(prim.l === 6);
    ok(prim.f === 7.0);
    ok(prim.d === 8.0);
  }
    
});




