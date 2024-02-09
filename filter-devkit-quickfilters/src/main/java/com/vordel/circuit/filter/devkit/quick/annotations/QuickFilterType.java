package com.vordel.circuit.filter.devkit.quick.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickFilterType {
	/**
	 * @return filter version
	 */
	int version() default 1;

	/**
	 * @return filter type name in the entity store (defaults to definition class name)
	 */
	String name() default "";

	/**
	 * @return policy studio category
	 */
	String category() default "";

	/**
	 * @return name of java resource which contains the palette icon
	 */
	String icon() default "";

	/**
	 * @return name of java resource which contains the declarative ui file
	 */
	String page();

	/**
	 * @return name of java resource which contains the filter resources
	 */
	String resources();
}
