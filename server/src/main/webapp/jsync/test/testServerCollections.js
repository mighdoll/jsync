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

/** inserts into a sequence on both client and server.  Also verifies a client sequence.clear() */
test("sync.serverSequence.insertClear", function() {
  var uniqueName = "seqTestTime-" + $sync.util.now();

  expect(8);
  withTestSubscription("sequence", modifySequence, seqChanged);

  function modifySequence(seq) {
  	var elem = $sync.test.nameObj();
    elem.name_(uniqueName);
	
  	seq.clear();
    seq.insert(elem);
  }

  function seqChanged(change) {	
  	var seq = change.target;
  	var seq2;
  	ok(seq.size() == 3);
  	matchSequenceNames(seq, [,"val",uniqueName]);
  	seq2 = seq.getAt(0);
  	ok(seq2.$kind === '$sync.sequence');
  	ok(seq2.size() == 3);
  	matchSequenceNames(seq2, ["chris","anya","bryan"]);
  }  
});

/** moves some sequence elements on both client and sever */
test("sync.serverSequence.move", function() {
  expect(10);
  withTestSubscription("moveSequence", moveElements, verify);	
  
  
  function moveElements(seq) {
    ok(seq.size() === 3);	
    matchSequenceNames(seq, ['a','b','c']);
    seq.moveAt(0, 1);		
    matchSequenceNames(seq, ['b','a','c']);
  }
  
  function verify(change) {	
	   // server moves 2,0 -> c,b,a
    matchSequenceNames(change.target, ['c','b','a']);
  }   
});

test("sync.serverSequence.remove", function() {
  withTestSubscription("removeSequence", removeElement, verify);
  
  function removeElement(seq) {
    matchSequenceNames(seq, ['a','b','c']);
    seq.removeAt(0);
    matchSequenceNames(seq, ['b','c']);
  }
  
  function verify(change) {	
	   // server removes(1) 
    matchSequenceNames(change.target, ['b']);
  }   
});

/** verify a sequence contains objects
 *  with name properties that match the matchArray.  'undefined'
 *  elements in the match array are skipped.
 */
function matchSequenceNames(seq, matchArray) {
  var i, match;
  for (i = 0; i < matchArray.length; i++) {
  	match = matchArray[i];
    if (match !== undefined) {
      ok(match === seq.getAt(i).name);
    }
  }		
}

test("sync.serverSequenceAdd", function() {
  withTestSubscription("serverSequenceAdd", addElement, verify);
  
  function addElement(ref) {
    ref.ref_(ref);
  }
  
  function verify(change) {
    var ref = change.target;
    ok(ref.ref.size() === 1);
  }
});

