package com.vordel.circuit.script.bind;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for automatic registration of {@link ExtensionContext}. Any
 * class with this annotation will be registered in the message 'extensions'
 * dictionary.
 * 
 * @author rdesaintleger@axway.com
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtensionPlugin {
	/**
	 * @return name of extension within the global dictionary. if empty, the full
	 *         class name will be used.
	 */
	String value() default "";
}
