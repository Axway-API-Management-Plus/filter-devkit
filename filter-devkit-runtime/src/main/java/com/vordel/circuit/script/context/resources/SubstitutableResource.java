package com.vordel.circuit.script.context.resources;

import com.vordel.common.Dictionary;

public interface SubstitutableResource<T> extends ContextResource {
	public T substitute(Dictionary dict);
}
