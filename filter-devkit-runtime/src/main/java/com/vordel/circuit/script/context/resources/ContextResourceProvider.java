package com.vordel.circuit.script.context.resources;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.common.Dictionary;
import com.vordel.config.Circuit;

public interface ContextResourceProvider {

	/**
	 * retrieve a reference to an Invocable.
	 * 
	 * @param dict dictionary to resolve delayed resources
	 * @param name name of invocable resource
	 * 
	 * @return invocable resource or null if none (if not invocable)
	 */
	public InvocableResource getInvocableResource(Dictionary dict, String name);

	/**
	 * retrieve a reference to a Substituable object
	 * 
	 * @param dict dictionary to resolve delayed resources
	 * @param name name of substituable resource
	 * 
	 * @return substituable or null if none (if not substituable)
	 */
	public SubstitutableResource<?> getSubstitutableResource(Dictionary dict, String name);

	/**
	 * Retrieve a reference to a KPS resource
	 * 
	 * @param dict dictionary to resolve delayed resources
	 * @param name name of kps resource
	 * 
	 * @return kps resource or null if none (if not kps)
	 */
	public KPSResource getKPSResource(Dictionary dict, String name);

	/**
	 * Retrieve the named context resource. Returned resource may be delayed.
	 * 
	 * @param name name of resource to retrieve
	 * @return configured resource, or null if non existant.
	 */
	public ContextResource getContextResource(String name);

	/**
	 * Retrieve the named context resource and resolve {@link DelayedResource}
	 * 
	 * @param dict dictionary to resolve delayed resources
	 * @param name name of resource to retrieve
	 * @return configured resource or nul if non existant.
	 */
	public ContextResource getContextResource(Dictionary dict, String name);

	/**
	 * Invoke the named resource (if applicable). This is a convenience method which
	 * first retrieve resource with
	 * {@link #getInvocableResource(Dictionary, String)} then invoke it with
	 * {@link InvocableResource#invoke(Circuit, Message)}. If the requested resource
	 * is not invocable an exception is thrown.
	 * 
	 * @param c    current cirsuit
	 * @param m    current message
	 * @param name name of the resource
	 * 
	 * @return resource call result
	 * @throws CircuitAbortException if an exception occurs or if resource is not
	 *                               invocable.
	 */
	public boolean invoke(Circuit c, Message m, String name) throws CircuitAbortException;

	/**
	 * Substitute the named resource (if applicable)
	 * 
	 * @param dict dictionary to resolve delayed resources
	 * @param name name of selector expression
	 * 
	 * @return result of resource substitution
	 */
	public Object substitute(Dictionary dict, String name);
}
