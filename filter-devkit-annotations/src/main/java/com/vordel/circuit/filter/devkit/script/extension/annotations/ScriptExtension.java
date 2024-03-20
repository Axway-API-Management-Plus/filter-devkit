package com.vordel.circuit.filter.devkit.script.extension.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for automatic registration of script extensions.
 * 
 * @author rdesaintleger@axway.com
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ScriptExtension {
	/**
	 * @return list of interfaces to be registered.
	 */
	Class<?>[] value() default {};
}
