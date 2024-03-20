package com.vordel.circuit.filter.devkit.context.resources;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;

/**
 * Interface which represent an API Gateway executable entity
 * 
 * @author rdesaintleger@axway.com
 */
public interface InvocableResource extends ContextResource {
	/**
	 * Invoke an API Gateway runnable. underlying object may be any kind of API
	 * Gateway executable object (Policies, Message Processors, Exported Class or
	 * Script Methods, etc...)
	 * 
	 * @param m current Message
	 * @return 'true' to signal success, 'false' to signal failure, 'null' if no
	 *         resource exists. interpretation of result depends of underlying
	 *         object
	 * @throws CircuitAbortException thrown by underlying object in case of error.
	 */
	public boolean invoke(Message m) throws CircuitAbortException;
}
