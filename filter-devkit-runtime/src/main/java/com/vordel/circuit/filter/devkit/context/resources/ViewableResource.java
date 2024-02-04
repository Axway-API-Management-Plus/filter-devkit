package com.vordel.circuit.filter.devkit.context.resources;

public interface ViewableResource {
	/**
	 * Retrieve resource object suitable for usage in juel expressions. For example,
	 * a cache will be returned as a Map and a KPS table will be returned as an
	 * incremental dictionary
	 * 
	 * @return object suitable for use by juel expressions
	 */
	public Object getResourceView();
}
