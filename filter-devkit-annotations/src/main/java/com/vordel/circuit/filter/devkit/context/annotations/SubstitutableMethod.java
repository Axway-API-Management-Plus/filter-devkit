package com.vordel.circuit.filter.devkit.context.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for methods exported as subtitutable. Annotated methods can
 * return any value but they can't throw exceptions (silenced but traced if log
 * level is set to debug).
 * 
 * @author rdesaintleger@axway.com
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface SubstitutableMethod {
	/**
	 * @return name of exported resource (defaults to method name)
	 */
	String value() default "";
}
