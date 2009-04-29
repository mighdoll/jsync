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
package com.digiting.sync.aspects;

/* marker trait -- any scala object mixing in Observable or any superclass
 * mixing in Observable will participate in Observation.
 * 
 * The scala object's property methods will be woven at class load time
 * or compile time by aspectJ to participate in the Observation scheme.  
 * 
 * Here's how the weaving works: property 'setters' of the form 
 * prop_$eq (as scala generates with 'var prop' syntax) are edited at class load time 
 * to include a call to Observation.notify()
 */
public interface Observable {

}
