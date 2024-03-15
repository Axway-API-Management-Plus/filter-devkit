package com.vordel.circuit.filter.devkit.quick.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickFilterConsumed {
	/**
	 * @return list of attributes consumed by filter
	 */
	String[] value() default {};
}
