package com.vordel.circuit.filter.devkit.context.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for methods exported as functions. Annotated methods must
 * return a boolean.
 * 
 * @author rdesaintleger@axway.com
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtensionFunction {
	/**
	 * @return name of exported resource (defaults to method name)
	 */
	String value() default "";
}
