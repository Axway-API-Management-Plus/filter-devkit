package com.vordel.circuit.filter.devkit.quick;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterComponent;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterField;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterType;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.es.Entity;

/**
 * Base class for Java Quick Filter development. Quick Filters must inherit this
 * abstract class and must be annotated with {@link QuickFilterType}. Each
 * filter field must have a specific setter annotated with
 * {@link QuickFilterField} or {@link QuickFilterComponent}. Each annotated
 * setter will have an entry in the associated filter typeset.
 * 
 * @author rdesaintleger@axway.com
 */
public abstract class JavaQuickFilterDefinition {
	/**
	 * Filter entry point. Similar to
	 * {@link MessageProcessor#invoke(Circuit, Message)}. The message processor
	 * argument is the actual class that will instantiate and use this definition
	 * class. It can be seen as the proxy class which is used by the API Gateway for
	 * message processing.
	 * 
	 * @param c current circuit
	 * @param m current message
	 * @param p message processor used as proxy class
	 * @return 'true' or false according to filter return status
	 * @throws CircuitAbortException if any error occurs
	 */
	public abstract boolean invokeFilter(Circuit c, Message m, MessageProcessor p) throws CircuitAbortException;

	/**
	 * Filter attach hook. Similar to
	 * {@link MessageProcessor#filterAttached(ConfigContext, Entity)}. Called when
	 * the configuration is deployed. This method is alled after all annotated
	 * setters have been invoked.
	 * 
	 * @param ctx    configuration context for this filter
	 * @param entity filter instance in the entity store
	 * @see JavaQuickFilterProcessor#filterAttached(ConfigContext, Entity)
	 */
	public abstract void attachFilter(ConfigContext ctx, Entity entity);

	/**
	 * Filter detach hook. Similar to {@link MessageProcessor#filterDetached()}.
	 * Called when the configuration is undeployed.
	 * 
	 * @see JavaQuickFilterProcessor#filterDetached()
	 */
	public abstract void detachFilter();
}
