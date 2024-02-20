package com.vordel.circuit.filter.devkit.context.resources;

import static com.vordel.circuit.filter.devkit.context.resources.SelectorResource.fromLiteral;

import javax.el.ELException;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.common.Dictionary;
import com.vordel.el.Selector;

public class InvocableExpressionResource implements InvocableResource, SubstitutableResource<Boolean> {
	private Selector<Boolean> selector;

	public InvocableExpressionResource(String expression) {
		this(fromLiteral(expression, Boolean.class, false));
	}

	public InvocableExpressionResource(Selector<Boolean> selector) {
		this.selector = selector;
	}

	@Override
	public Boolean substitute(Dictionary dict) {
		return selector == null ? null : selector.substitute(dict);
	}

	@Override
	public Boolean invoke(Message m) throws CircuitAbortException {
		return invoke(m, selector);
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
