package com.vordel.circuit.filter.devkit.context.resources;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.common.Dictionary;

public interface FunctionResource extends ContextResource {
	public Object invoke(Dictionary dict, Object... args) throws CircuitAbortException;
}
