package com.vordel.circuit.filter.devkit.context.resources;

import com.vordel.common.Dictionary;

/**
 * Represent any resource which can be computed an runtime without throwing
 * exception (returns <code>null</code> instead).
 * 
 * @author rdesaintleger@axway.com
 *
 * @param <T> substitution target coercion
 */
@FunctionalInterface
public interface SubstitutableResource<T> extends ContextResource {
	/**
	 * retrieve or compute value according to provided {@link Dictionary} object.
	 * 
	 * @param dict {@link Dictionary} used to compute return value.
	 * @return substitution result.
	 */
	public T substitute(Dictionary dict);
}
