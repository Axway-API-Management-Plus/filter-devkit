package com.vordel.circuit.ext.filter.quick;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickFilterField {
	/**
	 * @return name of the entity field
	 */
	String name();

	/**
	 * @return mapping of 'cardinality' attribute of field element
	 */
	String cardinality();

	/**
	 * @return mapping of 'type' attribute of field element
	 */
	String type();

	/**
	 * @return mapping of 'isKey' attribute of field element
	 */
	boolean isKey() default false;

	/**
	 * @return array of default values. A signel value will be set in the 'default' field attribute.
	 */
	String[] defaults() default {};
}
