package com.vordel.circuit.script.context.resources;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.config.Circuit;

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
	 * @param c current Circuit (policy)
	 * @param m current Message
	 * @return 'true' to signal success, 'false' to signal failure. interpretation
	 *         or result depends of underlying object
	 * @throws CircuitAbortException thrown by underlying object in case of error.
	 */
	public boolean invoke(Circuit c, Message m) throws CircuitAbortException;
}
