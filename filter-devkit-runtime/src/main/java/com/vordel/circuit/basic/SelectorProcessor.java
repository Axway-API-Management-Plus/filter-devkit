package com.vordel.circuit.basic;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.filter.devkit.context.resources.InvocableExpressionResource;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.el.ContextResourceResolver.ContextResourceDictionary;
import com.vordel.el.Selector;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;
import com.vordel.trace.Trace;

/**
 * Patch for the Evaluate Selector filter. It allows CircuitAbortException to be
 * thrown (So policies may be called using selector syntax). Implementation of
 * CircuitAbortException wrapping can be found in
 * {@link ContextResourceDictionary}.
 * 
 * @author rdesaintleger@axway.com
 */
public class SelectorProcessor extends MessageProcessor {
	/**
	 * Selector literal found in filter
	 */
	Selector<Boolean> selector;

	@Override
	public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
		super.filterAttached(ctx, entity);

		selector = new Selector<Boolean>(entity.getStringValue("expression"), Boolean.class);
	}

	public boolean invoke(Circuit p, Message m) throws CircuitAbortException {
		Boolean rc = InvocableExpressionResource.invoke(m, selector);

		if (Trace.isDebugEnabled()) {
			Trace.debug(String.format("evaluate %s = %s", selector, rc));
		}

		return rc;
	}
}
