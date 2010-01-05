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

/**
 * @param {String} subscription - name of the subscription to collect from the server 
 * @param {function} subscribedFn - called when the subscription arrives 
 * @param {function} ?watchedFn - called when+if the server modifies the subscribed
 *   objects after noticing modifications made by the subscribedFn.  Allows for testing
 *   server repsonses.
 * @param {function} ?subscribedAgainFn - if this function provided, the test fixture
 *   restarts the connection machine and then connects and resubscribes as if from a 
 *   newly connecting client, then calls the subscribedAgainFn.  Allows for testing
 *   data persistence across client connections.
 */
function withTestSubscription(subscription, subscribedFn, watchedFn, subscribedAgainFn) {  
  $sync.manager.setDefaultPartition("test");
  function begin() {
    stop();
    $sync.connect("/sync", {
      connected: connected
    });
  }
  
  /** called after connection is established  */
  function connected(connection) {
    connection.subscribe(subscription, "test", subscribed);
    $sync.manager.commit();
  }

  /** called after subscription is received */
  function subscribed(root) {
    $sync.manager.setDefaultPartition("test");
    subscribedFn(root);
    
    if (watchedFn) {
      $sync.observation.watch(root, changed); // wait for more changes
      $sync.manager.commit();
    } else if (subscribedAgainFn) {
	    subscribeAgain();
    } else {
      finish();
    }
  }
  
  var changeNoticed = false;
  
  function changed(change) {
    if (changeNoticed) // we're called for each change
      return;
    changeNoticed = true;
    watchedFn(change);
    if (subscribedAgainFn) {
      subscribeAgain();
    } else {
      finish();
    }
  }
  
  function subscribeAgain() {
    // reconnect to simulate a new client
    connection2 = $sync.connect("/sync", {
      connected: connected2
    });
    $sync.manager.commit();
    $sync.manager.reset();
    $sync.manager.setDefaultPartition("test");
  }
  
  /** called after second connection is established */
  function connected2(connection) {
    connection.subscribe(subscription, "test", subscribed2);
    $sync.manager.commit();
  }
  
  /** called after second connection's subscription is received */
  function subscribed2(root) {  	
	$sync.manager.setDefaultPartition("test");
	subscribedAgainFn(root);
	finish();
  }
  
  function finish() {
    $sync.manager.reset();
	start();  	
  }
  
  begin();
}

