package com.vordel.circuit.filter.devkit.context.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used for parameter injection in {@link InvocableMethod} and
 * {@link SubstitutableMethod}. It will retrieve attribute in the target
 * dictionary using the provided name.
 * 
 * @author rdesaintleger@axway.com
 */
@Target({ ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface DictionaryAttribute {
	/**
	 * @return name of attribute to be retrieved from the target Dictionary
	 */
	String value();
}
