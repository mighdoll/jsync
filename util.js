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
    var that = {};

    that.doNothing = function() {};
	
    that.error = function() {
        $debug.assert(false); };

    /** return true if the object is an array */
    that.isArray = function (value) {
        return value &&
        typeof value === 'object' &&
        typeof value.length === 'number' &&
        typeof value.splice === 'function' &&
        !(value.propertyIsEnumerable('length')); };
	
    /* calls function on each element of object array or just on objct */
    that.optionalArray = function(objOrArray, fn) {
        if (!objOrArray) {
            return; }
		
        if ($sync.util.isArray(objOrArray)) {
            objOrArray.each(fn);
        } else {
            fn(objOrArray); } };

    that.max = function(a,b) {
        return a >= b ? a : b; };

    /* create a new javascript object with the provided prototype */
    that.createObject = function(proto) {
        var fn = function() {};
        fn.prototype = proto;
        return new fn(); };

    /* copy an object, does not functions or recreate the prototype chain */
    that.copyObjectData = function(src, target) {
        var prop;
        for (prop in src) {
            if (src.hasOwnProperty(prop)
                && typeof src[prop] !== 'function') {
            target[prop] = src[prop]; } }
        return target; };

    return that; })();

