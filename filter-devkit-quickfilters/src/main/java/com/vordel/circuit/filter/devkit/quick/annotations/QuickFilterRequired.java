package com.vordel.circuit.filter.devkit.quick.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickFilterRequired {
	/**
	 * @return list of attributes required by filter
	 */
	String[] value() default {};
}
