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

/* misc utility functions */
$sync.util = (function() {
  var self = {
    doNothing: function() {
    },
    
    error: function() {
      $debug.assert(false);
    },
    
    /** return true if the object is an array */
    isArray: function(value) {
      return value &&
      typeof value === 'object' &&
      typeof value.length === 'number' &&
      typeof value.splice === 'function' &&
      !(value.propertyIsEnumerable('length'));
    },
    
    /** calls function on each element of object array or, just on object if it is truthy */
    each: function(objOrArray, fn) {
      if (!objOrArray) {
        return;
      }
      
      if ($sync.util.isArray(objOrArray)) {
        self.arrayFind(objOrArray, function(elem) {
          fn(elem); // ignore return value
        });
      }
      else {
        fn(objOrArray);
      }
    },
    
    max: function(a, b) {
      return a >= b ? a : b;
    },
    
    /* create a new javascript object with the provided prototype */
    createObject: function(proto) {
      function fn() { }
      fn.prototype = proto;
      return new fn();
    },
    
    /* copy an object; does not copy functions or recreate the prototype chain */
    copyObjectData: function(src, target) {
      var prop;
      for (prop in src) {
        if (src.hasOwnProperty(prop) &&
        typeof src[prop] !== 'function') {
          target[prop] = src[prop];
        }
      }
      return target;
    },
    
    /** look for an element in an array  
     *
     * @return true if the element is found, otherwise false
     */
    arrayFindElem: function(array, elem) {
      for (i = 0; i < array.length; i++) {
        if (array[i] === elem) 
          return true;
      }
      return false;
    },
    
    /** run a function on each defined element in an array, until the function
     * returns a value other than undefined.
     *
     * @return the value returned by the supplied function, or false
     */
    arrayFind: function(array, eachFn) {
      var i;
      var result;
      
      for (i = 0; (result === undefined) && i < array.length; i++) {
        if (array[i] !== undefined) 
          result = eachFn(array[i]);
      }
      return result;
    },
    
    /** return milliseconds since 1970 */
    now: function() {
      return new Date().getTime();
    },
    
    /** return a concise printout of local fields */
    fieldsToString: function(obj) {
      var str = "";
      for (var field in obj) {
        if (typeof obj[field] !== 'function' &&
        obj.hasOwnProperty(field)) {
          str += field + ":" + obj[field] + "  ";
        }
      }
      return str;
    },
    
    /** copy local fields from extension to obj */ 
    extend: function(obj, extension) {
      for (var field in extension) {
        if (extension.hasOwnProperty(field)) {
          obj[field] = extension[field];
        }
      }
    }
  };
  
  return self;
})();


