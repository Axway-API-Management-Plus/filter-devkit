package com.vordel.circuit.filter.devkit.context.resources;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.common.Dictionary;
import com.vordel.el.Selector;

public class InvocableExpressionResource implements InvocableResource, SubstitutableResource<Boolean> {
	private Selector<Boolean> selector;

	public InvocableExpressionResource(String expression) {
		this(SelectorResource.fromLiteral(expression, Boolean.class, false));
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
		return SelectorResource.invoke(m, selector);
	}
}
