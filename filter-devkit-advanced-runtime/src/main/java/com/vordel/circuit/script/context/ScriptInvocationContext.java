package com.vordel.circuit.script.context;

import java.util.List;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.script.context.resources.CacheResource;
import com.vordel.circuit.script.context.resources.ContextResourceProvider;
import com.vordel.common.Dictionary;

/**
 * This interface allow an API Gateway component to access programmatically to
 * common resources.
 * 
 * @author rdesaintleger@axway.com
 */
public interface ScriptInvocationContext extends ContextResourceProvider {
	/**
	 * get status of previous executed filter.
	 * 
	 * @param m     current message
	 * @param index negative index of filter (-1 is last filter)
	 * 
	 * @return status of filter execution
	 * @throws CircuitAbortException is previous filter invocation returned an
	 *                               exception
	 */
	public boolean getPreviousStatus(Message m, int index) throws CircuitAbortException;

	/**
	 * retrieves the named message attibute
	 * 
	 * @param m    current message
	 * @param name name of the target attribute
	 * 
	 * @return current attribute value
	 */
	public Object getMessageAttribute(Message m, String name);

	/**
	 * removes the named message attibute
	 * 
	 * @param m    current message
	 * @param name name of the target attribute
	 * 
	 * @return current attribute value
	 */
	public Object removeMessageAttribute(Message m, String name);

	/**
	 * sets the value of the named message attibute
	 * 
	 * @param m     current message
	 * @param name  - name of the target attribute
	 * @param value - value of the target attribute
	 * 
	 * @return previous value of attribute (if any)
	 */
	public Object setMessageAttribute(Message m, String name, Object value);

	/**
	 * Retrieve the given user attribute (stored in 'attribute.lookup.list')
	 * 
	 * @param m         current message
	 * @param name      name of the attribute
	 * @param namespace namespace of the attribute, may be null for no namespace.
	 * 
	 * @return attribute values
	 */
	public List<String> getUserAttribute(Message m, String name, String namespace);

	/**
	 * Removes the given user attribute (stored in 'attribute.lookup.list')
	 * 
	 * @param m         current message
	 * @param name      name of the attribute
	 * @param namespace namespace of the attribute, may be null for no namespace.
	 */
	public void removeUserAttribute(Message m, String name, String namespace);

	/**
	 * Adds a value to the list of specified user attribute (stored in
	 * 'attribute.lookup.list')
	 * 
	 * @param m         current message
	 * @param name      name of the attribute
	 * @param namespace namespace of the attribute, may be null for no namespace.
	 * @param value     value to be added
	 */
	public void addUserAttribute(Message m, String name, String namespace, String value);

	/**
	 * Replaces the specified user attribute list by the given value
	 * 
	 * @param m         current message
	 * @param name      name of the attribute
	 * @param namespace namespace of the attribute, may be null for no namespace.
	 * @param value     value to be set
	 */
	public void setUserAttribute(Message m, String name, String namespace, String value);

	/**
	 * evaluates the given selector expression. The method
	 * {@link #substitute(Dictionary, String)} should be used for better performances.
	 * 
	 * @param m          current message
	 * @param expression expression to be evaluated
	 * 
	 * @return evaluation result
	 */
	@Deprecated
	Object evaluateExpression(Dictionary m, String expression);

	/**
	 * Retrieve the Cache instance for the given resource
	 * 
	 * @param dict TODO
	 * @param name cache resource name
	 * 
	 * @return cache resource or null if none (if not cache or not kps)
	 */
	public CacheResource getCacheResource(Dictionary dict, String name);

	/**
	 * Set the http status for the current message
	 * 
	 * @param m      current message
	 * @param status http status code
	 */
	public void setHttpStatus(Message m, int status);
}
