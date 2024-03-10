package com.vordel.circuit.filter.devkit.script.advanced;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.context.resources.CacheResource;
import com.vordel.circuit.filter.devkit.context.resources.ContextResource;
import com.vordel.circuit.filter.devkit.context.resources.ContextResourceProvider;
import com.vordel.circuit.filter.devkit.context.resources.InvocableResource;
import com.vordel.circuit.filter.devkit.context.resources.KPSResource;
import com.vordel.common.Dictionary;

/**
 * Base top level functions exported to all scripts runtime.
 * 
 * @author rdesaintleger@axway.com
 */
public interface AdvancedScriptRuntime {
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

	/**
	 * Retrieve a reference to a Cache resource
	 * 
	 * @param name name of cache resource
	 * 
	 * @return cache resource or null if none (if not cache)
	 */
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
