package com.vordel.circuit.filter.devkit.context.resources;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.common.Dictionary;

/**
 * Base interface representing a set of attached resources.
 * 
 * @author rdesaintleger@axway.com
 */
public interface ContextResourceProvider {

	/**
	 * retrieve a reference to an Invocable.
	 * 
	 * @param name name of invocable resource
	 * 
	 * @return invocable resource or null if none (if not invocable)
	 */
	public InvocableResource getInvocableResource(String name);

	/**
	 * Retrieve a reference to a Function
	 * 
	 * @param name name of function resource
	 * 
	 * @return function resource or null if none (if not function)
	 */
	public FunctionResource getFunctionResource(String name);

	/**
	 * retrieve a reference to a Substituable object
	 * 
	 * @param name name of substituable resource
	 * 
	 * @return substituable or null if none (if not substituable)
	 */
	public SubstitutableResource<?> getSubstitutableResource(String name);

	/**
	 * Retrieve a reference to a KPS resource
	 * 
	 * @param name name of kps resource
	 * 
	 * @return kps resource or null if none (if not kps)
	 */
	public KPSResource getKPSResource(String name);

	/**
	 * Retrieve the named context resource.
	 * 
	 * @param name name of resource to retrieve
	 * @return configured resource, or null if non existent.
	 */
	public ContextResource getContextResource(String name);

	/**
	 * Invoke the named resource (if applicable). This is a convenience method which
	 * first retrieve resource with {@link #getInvocableResource(String)} then
	 * invoke it with {@link InvocableResource#invoke(Message)}. If the requested
	 * resource is not invocable an exception is thrown. If the requested resource
	 * is {@code null}, null is returned.
	 * 
	 * @param m    current message
	 * @param name name of the resource
	 * 
	 * @return resource call result
	 * @throws CircuitAbortException if an exception occurs or if resource is not
	 *                               invocable.
	 */
	public Boolean invoke(Message m, String name) throws CircuitAbortException;

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
