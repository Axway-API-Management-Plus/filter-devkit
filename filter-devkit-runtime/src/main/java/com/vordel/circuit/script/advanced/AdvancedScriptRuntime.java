package com.vordel.circuit.script.advanced;

import javax.script.ScriptException;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.script.context.resources.CacheResource;
import com.vordel.circuit.script.context.resources.ContextResource;
import com.vordel.circuit.script.context.resources.ContextResourceProvider;
import com.vordel.circuit.script.context.resources.InvocableResource;
import com.vordel.circuit.script.context.resources.KPSResource;
import com.vordel.circuit.script.jaxrs.ScriptWebComponent;
import com.vordel.common.Dictionary;
import com.vordel.config.Circuit;

import groovy.lang.Script;

public interface AdvancedScriptRuntime {
	/**
	 * By default, exceptions thrown by script filter will be enclosed in
	 * ScriptException. This behavior can be changed and CircuitException can be
	 * thrown unwrapped.
	 * 
	 * @param unwrap {@code true} to unwrap {@link CircuitAbortException},
	 *               {@code false} for default behavior.
	 * @throws ScriptException
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
	 * @throws ScriptException
	 * 
	 * @apiNote this method can only be used when calling from initial eval or
	 *          attach script function
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
	 * @param m    current message
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
	 * bind annotated groovy methods to exportable context (usable in selectors)
	 * 
	 * @param script
	 * @throws ScriptException
	 * 
	 * @apiNote this method can only be used once and only when calling from initial
	 *          eval or attach script function
	 */
	void reflectExtensions(Script script) throws ScriptException;

	/**
	 * scan a groovy script for invoke() and detach() with parameter injection. As a
	 * side effect CircuitAbortExceptions are not wrapped (since this is a direct
	 * Java reflective call).
	 * 
	 * @param script
	 * @throws ScriptException
	 * 
	 * @apiNote if no method matches using injection regular script function call is
	 *          applied
	 */
	void reflectEntryPoints(Script script) throws ScriptException;

	/**
	 * disable the invoke method and replace if by a JAXRS service invocation.
	 * 
	 * @param jaxrs         service reflected from current groovy script
	 * @param reportNoMatch if {@code true}, the service will return {@code false}
	 *                      if the JAXRS service fails
	 * @throws ScriptException
	 * 
	 * @apiNote this method can only be used when calling from initial eval or
	 *          attach script function.
	 */
	void setScriptWebComponent(ScriptWebComponent jaxrs, boolean reportNoMatch) throws ScriptException;

	/**
	 * retrieve the script {@link ContextResourceProvider} for export to
	 * {@link Message} attribute.
	 * 
	 * @return the script {@link ContextResourceProvider} with exported functions
	 *         and resources.
	 */
	ContextResourceProvider getExportedResources();
}
