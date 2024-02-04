package com.vordel.circuit.filter.devkit.context.resources;

import com.vordel.common.Dictionary;

public interface SubstitutableResource<T> extends ContextResource {
	public T substitute(Dictionary dict);
}
