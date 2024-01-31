package com.vordel.circuit.script.context.resources;

import com.vordel.el.ContextResourceResolver;
import com.vordel.el.Selector;

public abstract class AbstractContextResourceProvider implements ContextResourceProvider {
	static {
		Selector.addResolver(ContextResourceResolver.getInstance());
	}
	
	@Override
	public ContextResource getContextResource(String name) {
		ContextResource resource = getContextResource(name);

		return resource;
	}

	@Override
	public final InvocableResource getInvocableResource(String name) {
		ContextResource resource = getContextResource(name);

		return resource instanceof InvocableResource ? (InvocableResource) resource : null;
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
}
