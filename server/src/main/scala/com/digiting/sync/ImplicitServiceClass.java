package com.digiting.sync;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marker annotation on a method to expose a java/scala method as a remote procedure call endpoint 
 * for implicit sync calls */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface ImplicitServiceClass {
  String value() default "";
}
