package com.vordel.circuit.filter.devkit.context.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used for parameter injection in {@link InvocableMethod} and
 * {@link SubstitutableMethod}. It will create a selector expression from the
 * given value and will apply the selector on the target dictionary.
 * 
 * @author rdesaintleger@axway.com
 */
@Target({ ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface SelectorExpression {
	/**
	 * @return JUEL expression to be applied to retrieve injectable value.
	 */
	String value();
}
