package com.vordel.circuit.basic;

import javax.el.ELException;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.common.Dictionary;
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
		Boolean rc = invoke(m, selector);

		if (Trace.isDebugEnabled()) {
			Trace.debug(String.format("evaluate %s = %s", selector, rc));
		}

		return rc;
	}

	public static final Boolean invoke(Dictionary dict, Selector<Boolean> selector) throws CircuitAbortException {
		Boolean rc = null;

		try {
			/* try to retrieve selector value, but keep exception */
			rc = selector.substitute(dict, true);
		} catch (Exception e) {
			if (e instanceof ELException) {
				/* examine cause */
				Throwable cause = e.getCause();

				if (cause instanceof CircuitAbortException) {
					/* This is a CircuitAbortException, relay it */
					throw (CircuitAbortException) cause;
				}
			}

			throw new CircuitAbortException(String.format("Could not evaluate boolean expression %s", selector.getLiteral()), e);
		}

		if (null == rc) {
			/* keep regular eval selector behavior */
			throw new CircuitAbortException(String.format("Could not evaluate boolean expression %s", selector.getLiteral()));
		}

		return rc;
	}
}
