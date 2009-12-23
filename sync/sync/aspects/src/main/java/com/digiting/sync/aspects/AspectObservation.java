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

//import java.lang.reflect.Method;
import java.util.ArrayList;

/** Register a function with registerListener and it will be called whenever an observable
 *  object is changed.  */
public class AspectObservation {

  static ArrayList<ObserveListener> listeners = new ArrayList<ObserveListener>();
//  static PropertyGetter getter = new DefaultPropertyGetter();

  static public void registerListener(ObserveListener listener) {
    listeners.add(listener);
  }

  static void notify(Object target, String name, Object newValue, Object oldValue) {
//		System.out.println("aspect notification received: ." + name + "=" + newValue
//				+ " oldValue: " + oldValue); // + " on: " + target);
    for (ObserveListener listener : listeners) {
      listener.change(target, name, newValue, oldValue);
    }
  }
  
//  static public void registerGetter(PropertyGetter propertyGetter) {
//	getter = propertyGetter;
//  }
//  
//  static Object getProperty(Object target) {
//	return null;
//  }
//  
//  static class DefaultPropertyGetter implements PropertyGetter {
//	public Object getProperty(String property, Object instance) {
//	  Object value = null;	
//      try {
//          final Class<?>[] noParams = new Class[0];
//          Method getter = instance.getClass().getDeclaredMethod(property, noParams);
//          value = getter.invoke(instance);
//        } catch (Exception e) {
//          System.err.println("error finding or using getter for property: " + property + " on object: " + instance);
//          System.err.println(e);
//          e.printStackTrace(System.err);
//        }
//      return value;
//	}
//  }
  
}
