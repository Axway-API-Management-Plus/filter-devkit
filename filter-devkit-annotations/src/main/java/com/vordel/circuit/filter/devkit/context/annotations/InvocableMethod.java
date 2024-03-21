package com.vordel.circuit.filter.devkit.context.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for methods exported as invocables. Annotated methods must
 * return a boolean and arguments are injected at runtime.
 * 
 * @author rdesaintleger@axway.com
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface InvocableMethod {
	/**
	 * @return name of exported resource (defaults to method name)
	 */
	String value() default "";
}
