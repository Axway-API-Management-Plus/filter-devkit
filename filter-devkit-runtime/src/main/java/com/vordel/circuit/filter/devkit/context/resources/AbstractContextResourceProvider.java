package com.vordel.circuit.filter.devkit.context.resources;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.common.Dictionary;
import com.vordel.el.ContextResourceResolver;
import com.vordel.el.Selector;
import com.vordel.trace.Trace;

public abstract class AbstractContextResourceProvider implements ContextResourceProvider {
	static {
		Selector.addResolver(ContextResourceResolver.getInstance());
	}

	@Override
	public abstract ContextResource getContextResource(String name);

	@Override
	public final InvocableResource getInvocableResource(String name) {
		ContextResource resource = getContextResource(name);

		return resource instanceof InvocableResource ? (InvocableResource) resource : null;
	}

	@Override
	public final FunctionResource getFunctionResource(String name) {
		ContextResource resource = getContextResource(name);

		return resource instanceof FunctionResource ? (FunctionResource) resource : null;
	}

	@Override
	public final SubstitutableResource<?> getSubstitutableResource(String name) {
		ContextResource resource = getContextResource(name);

		return resource instanceof SubstitutableResource ? (SubstitutableResource<?>) resource : null;
	}

	@Override
	public final KPSResource getKPSResource(String name) {
		ContextResource resource = getContextResource(name);

		return resource instanceof KPSResource ? (KPSResource) resource : null;
	}

	@Override
	public final Boolean invoke(Message m, String name) throws CircuitAbortException {
		ContextResource resource = getContextResource(name);

		if (!(resource instanceof InvocableResource)) {
			throw new CircuitAbortException(String.format("resource '%s' is not invocable", name));
		}

		if (m == null) {
			throw new CircuitAbortException("no message context");
		}

		return ((InvocableResource) resource).invoke(m);
	}

	@Override
	public final Object substitute(Dictionary dict, String name) {
		ContextResource resource = getContextResource(name);
		Object result = null;

		if (resource instanceof SubstitutableResource) {
			result = ((SubstitutableResource<?>) resource).substitute(dict);
		} else {
			Trace.error(String.format("resource '%s' is not substitutable", name));
		}

		return result;
	}
}
