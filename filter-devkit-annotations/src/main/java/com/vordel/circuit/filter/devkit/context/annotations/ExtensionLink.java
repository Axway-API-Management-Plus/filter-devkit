package com.vordel.circuit.filter.devkit.context.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inicates to the annotation processor that this class must be loaded in the
 * same class loader of the specified class
 * 
 * @author rdesaintleger@axway.com
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtensionLink {
	/**
	 * @return class annotated with {@link ExtensionLibraries}
	 */
	Class<?> value();
}
