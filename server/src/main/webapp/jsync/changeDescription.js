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
var $sync = $sync || {};

/**
 * Description of changes to an observable object or collection
 * 
 * @param {Object} changeType
 * @param {Object} target
 * @param {Object} params
 *
 */
$sync.changeDescription = function(changeType, target, params) {
  var self = {  
    target:target,
    changeType:changeType,
    source:$sync.observation.currentMutator(),
    
    toString:function() {
       return ($sync.util.fieldsToString(self));
    }    
  };
  $.extend(self, params);
  
  validateParams();
//  $log.log("created: " + self);
  return self;
  
  
  function validateParams() {
    if (self.changeType === 'property') {
      $debug.assert(params.hasOwnProperty('oldValue'));
      $debug.assert(params.property);
    } else if (self.changeType == 'edit') {
      // LATER validate these parameters too
    } else if (self.changeType == 'create') {
    } else if (self.changeType == 'initial') {
      $debug.assert(params.property);
    } else {
      $debug.fail("unexpected change type: " + self.changeType);
    }
  }

};

/* Here are some examples of the change description object that's passed
 * to observation functions.
 *
 * from $sync.createSyncable(..)
 *   desc = { changeType: "create" }
 *   
 * from objA.fieldB_(10);
 *   desc = { changeType: "property",
 *            property: "fieldB",
 *            oldValue:null }
 *
 * from set.clear(newElem);
 *   desc = { changeType: "edit",
 *            clear:true }
 *            
 * from set.put(newElem);
 *   desc = { changeType: "edit",
 *            put: newElem }
 *
 * from sequence.insert(newElem);  
 *   desc = { changeType: "edit",
 *            insertAt: {index:101, elem:someObj } }
 *
 * from set.remove(newElem);
 *   desc = { changeType: "edit",
 *            remove: newElem }
 * 
 */

