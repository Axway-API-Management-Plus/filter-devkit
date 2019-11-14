package com.vordel.circuit.ext.filter.quick;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickFilterType {
	/**
	 * @return filter type name in the entity store
	 */
	String name();

	/**
	 * @return filter version
	 */
	int version() default 1;

	/**
	 * @return name of java resource which contains the declarative ui file
	 */
	String ui();

	/**
	 * @return name of java resource which contains the filter properties
	 */
	String resources();
	
	/**
	 * @return extends clause for entity type
	 */
	String extend() default "Filter";
}
