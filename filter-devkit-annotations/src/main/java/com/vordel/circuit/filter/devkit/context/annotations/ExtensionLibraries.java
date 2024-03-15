package com.vordel.circuit.filter.devkit.context.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for class loading 'child first' of extensions modules and
 * contexts. values provided can include directories. Selector syntax is
 * supported (using deploy runtime resolution)
 * 
 * @author rdesaintleger@axway.com
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtensionLibraries {
	/**
	 * @return list of jar files or directories containing jars
	 */
	String[] value() default {};

	/**
	 * additional set of classes to be loaded in the same class loader. This is
	 * handy if logic reside in /ext/lib and can't be implemented in a single class.
	 * 
	 * @return list of classes which must be loaded in the 'child first' class
	 *         loader
	 */
	String[] classes() default {};
}
