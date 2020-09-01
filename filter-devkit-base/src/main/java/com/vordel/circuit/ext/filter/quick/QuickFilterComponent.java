package com.vordel.circuit.ext.filter.quick;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickFilterComponent {
	/**
	 * @return name of the entity component
	 */
	String name();

	/**
	 * @return mapping of 'cardinality' attribute of field element
	 */
	String cardinality() default "*";
}
