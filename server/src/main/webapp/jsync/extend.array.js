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

/* TODO Get rid of these... It seems presumptions to extend core objects
 * like Array in a library intended for others to use. */

/* extend Array with some handy functions */
(function() {
    var method = function(cls, methodName, func) {
        cls.prototype[methodName] || (cls.prototype[methodName] = func);
    }
    
    /** calls @param fn for each element in the array.
     * iteration stops early if fn() returns a value */
    method(Array, 'eachCheck', function(fn) {
        var result = false;

        for (var i = 0; !result && i < this.length; i++) {
            if (typeof this[i] != 'undefined') {
                result = fn(this[i]); } }
        return result; });
})();

