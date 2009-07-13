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

import org.aspectj.lang.annotation.SuppressAjWarnings;
import java.lang.reflect.*;
import com.digiting.sync.aspects.Observable;

public aspect Observe {
  pointcut setScalaProperty() : execution(* Observable+.*_$eq(*));

  @SuppressAjWarnings	// don't warn that it doesn't match -- we weave at load time from a separate project
  void around(Object target, Object newVal): setScalaProperty() && args(newVal) && target(target){
    Object oldVal = null;
    String methodName = thisJoinPointStaticPart.getSignature().getName();
    String propName = methodName.substring(0, methodName.length() - 4);
    try {
      final Class<?>[] noParams = new Class[0];
      Method getter = target.getClass().getDeclaredMethod(propName, noParams);
      oldVal = getter.invoke(target);
    } catch (Exception e) {
      System.err.println("error finding or using getter for property: " + propName + " on object: " + target);
      System.err.println(e);
      e.printStackTrace(System.err);
    }
    proceed(target, newVal);
    AspectObservation.notify(target, propName, newVal, oldVal);
  }
}
