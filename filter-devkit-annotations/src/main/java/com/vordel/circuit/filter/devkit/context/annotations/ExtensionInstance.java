package com.vordel.circuit.filter.devkit.context.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for automatic registration of extension loadable modules
 * and interfaces.
 * 
 * @author rdesaintleger@axway.com
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtensionInstance {
	/**
	 * @return list of interfaces to be registered.
	 */
	Class<?>[] value() default {};
}
