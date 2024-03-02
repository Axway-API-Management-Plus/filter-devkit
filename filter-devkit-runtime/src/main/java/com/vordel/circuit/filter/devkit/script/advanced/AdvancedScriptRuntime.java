package com.vordel.circuit.filter.devkit.script.advanced;

import javax.script.ScriptException;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.context.resources.CacheResource;
import com.vordel.circuit.filter.devkit.context.resources.ContextResource;
import com.vordel.circuit.filter.devkit.context.resources.ContextResourceProvider;
import com.vordel.circuit.filter.devkit.context.resources.InvocableResource;
import com.vordel.circuit.filter.devkit.context.resources.KPSResource;
import com.vordel.common.Dictionary;
import com.vordel.config.Circuit;

public interface AdvancedScriptRuntime {
	/**
	 * By default, exceptions thrown by script filter will be enclosed in
	 * ScriptException. This behavior can be changed and CircuitException can be
	 * thrown unwrapped.
	 * 
	 * @param unwrap {@code true} to unwrap {@link CircuitAbortException},
	 *               {@code false} for default behavior.
	 * @throws ScriptException if this method is called outside script attachment
	 *                         phase
	 */
	void setUnwrapCircuitAbortException(boolean unwrap) throws ScriptException;

	/**
	 * s By default, the script invoke function is called only with the message
	 * parameter. the given parameter allows the {@link Circuit} to be passed as
	 * first argument (like in filters).
	 * 
	 * @param extended if {@code false}, invoke is called with {@link Message} as
	 *                 single parameter (Ex: {@code invoke(message)}). if
	 *                 {@code true}, {@link Circuit} and {@link Message} are used
	 *                 respectively as first and second parameter (Ex:
	 *                 {@code invoke(circuit, message)})
	 * @throws ScriptException if this method is called outside script attachment
	 *                         phase
	 */
	void setExtendedInvoke(boolean extended) throws ScriptException;

	/**
	 * Retrieve the named context resource.
	 * 
	 * @param name name of resource to retrieve
	 * @return configured resource, or null if non existent.
	 */
	ContextResource getContextResource(String name);

	/**
	 * retrieve a reference to an Invocable.
	 * 
	 * @param name name of invocable resource
	 * 
	 * @return invocable resource or null if none (if not invocable)
	 */
	InvocableResource getInvocableResource(String name);

	/**
	 * Retrieve a reference to a KPS resource
	 * 
	 * @param name name of kps resource
	 * 
	 * @return kps resource or null if none (if not kps)
	 */
	KPSResource getKPSResource(String name);

	CacheResource getCacheResource(String name);

	/**
	 * Invoke the named resource (if applicable). This is a convenience method which
	 * first retrieve resource with {@link #getInvocableResource(String)} then
	 * invoke it with {@link InvocableResource#invoke(Message)}. If the requested
	 * resource is not invocable an exception is thrown. If the requested resource
	 * is {@code null}, null is returned.
	 * 
	 * @param msg  current message
	 * @param name name of the resource
	 * 
	 * @return resource call result
	 * @throws CircuitAbortException if an exception occurs or if resource is not
	 *                               invocable.
	 */
	Boolean invokeResource(Message msg, String name) throws CircuitAbortException;

	/**
	 * Substitute the named resource (if applicable)
	 * 
	 * @param dict dictionary to resolve delayed resources
	 * @param name name of selector expression
	 * 
	 * @return result of resource substitution
	 */
	Object substituteResource(Dictionary dict, String name);

	/**
	 * retrieve the script {@link ContextResourceProvider} for export to
	 * {@link Message} attribute.
	 * 
	 * @return the script {@link ContextResourceProvider} with exported functions
	 *         and resources.
	 */
	ContextResourceProvider getExportedResources();

	/**
	 * This method allow to retrieve Filter name for debugging purposes
	 * 
	 * @return Filter name for this script
	 */
	String getFilterName();
}
