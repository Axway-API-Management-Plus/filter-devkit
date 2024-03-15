package com.vordel.circuit.filter.devkit.quick.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker class for Java Quick Filters. This annotation will trigger filter code
 * generation for API Gateway and Policy Studio. Build system must be adapted
 * for the target runtime (OSGi bundle for Policy Studio). The base distribution
 * of the FDK includes packaging in a single jar for both API Gateway and Policy
 * Studio.
 * 
 * @author rdesaintleger@axway.com
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickFilterType {
	/**
	 * @return filter version
	 */
	int version() default 1;

	/**
	 * @return filter type name in the entity store (defaults to definition class
	 *         name)
	 */
	String name() default "";

	/**
	 * @return policy studio category see AbstractJavaQuickFilterDefinition in the
	 *         tools project for a list of supported categories.
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
