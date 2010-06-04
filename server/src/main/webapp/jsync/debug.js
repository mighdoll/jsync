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
 *  Debug assertions
 */
var $debug = (function() {

  // manually copy array elements.  Array.concat doesn't work on built in arguments pseudo-array
  function concatArray(a, aStart, b, bStart) {
    var i, result = [];
    for (i = aStart; i < a.length; i++) {
      result.push(a[i]);
    }
    for (i = bStart; i < b.length; i++) {
      result.push(b[i]);
    }
    return result;
  }
  
  // when logging, we pass the logging arguments along to the underlying logger 
  // rather than concatenating them here, so e.g. firebug can pretty print appropriately
  var self = {
    assert: function(test) {
      var args;
      if (!test) {
        args = concatArray(["ASSERT: "], 0, arguments, 1);
        $log.debug.apply($log, args);  
        debugger;
      }
    },
    
    fail: function() {
      $log.error.apply($log, arguments);
      debugger;
    }
  };
  return self;
})();
