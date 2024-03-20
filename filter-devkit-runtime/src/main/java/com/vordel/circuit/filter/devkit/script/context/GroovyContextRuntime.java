package com.vordel.circuit.filter.devkit.script.context;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.common.Dictionary;

public interface GroovyContextRuntime extends ScriptContextRuntime {
	/**
	 * convenience method to invoke function. kept to groovy specific runtime
	 * because of the variable arguments
	 * 
	 * @param dict dictionary used as first function argument. usually this is the
	 *             current {@link Message}. if not compatible with function first
	 *             argument, null will be provided by the underlying call.
	 * @param name name of function resource
	 * @param args function arguments (variable)
	 * @return function result
	 * @throws CircuitAbortException if any error occurs
	 */
	Object invokeFunction(Dictionary dict, String name, Object... args) throws CircuitAbortException;
}
