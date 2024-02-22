package com.vordel.circuit.filter.devkit.context.resources;

import static com.vordel.circuit.filter.devkit.context.resources.SelectorResource.fromLiteral;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.basic.SelectorProcessor;
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
		return SelectorProcessor.invoke(m, selector);
	}
}
