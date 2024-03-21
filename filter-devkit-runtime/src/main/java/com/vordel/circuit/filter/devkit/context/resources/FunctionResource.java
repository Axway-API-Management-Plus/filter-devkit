package com.vordel.circuit.filter.devkit.context.resources;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionFunction;
import com.vordel.common.Dictionary;

/**
 * This class represent an exported Java Method using {@link ExtensionFunction}.
 * Its purpose is to call any Java method with variable arguments.
 * 
 * @author rdesaintleger@axway.com
 */
@FunctionalInterface
public interface FunctionResource extends ContextResource {
	/**
	 * Invoke the target Java Method
	 * 
	 * @param dict current dictionary (usually a {@link Message} object). the target
	 *             invocation will silently resolve this value to null if the
	 *             provided argument is not compatible with the target method
	 *             signature is not compatible with this object.
	 * @param args variable array of method arguments (excluding the first
	 *             dictionary parameter). Each value will be coerced to request type
	 *             in target method signature.
	 * @return the underlying method return value
	 * @throws CircuitAbortException can be thrown by the underlying function.
	 */
	public Object invoke(Dictionary dict, Object... args) throws CircuitAbortException;
}
