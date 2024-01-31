package com.vordel.circuit.script.context.resources;

import com.vordel.common.Dictionary;
import com.vordel.el.ContextResourceResolver;
import com.vordel.el.Selector;

public abstract class AbstractContextResourceProvider implements ContextResourceProvider {
	static {
		Selector.addResolver(ContextResourceResolver.getInstance());
	}
	
	@Override
	public final ContextResource getContextResource(Dictionary dict, String name) {
		ContextResource resource = getContextResource(name);

		while ((resource != null) && (dict != null) && (resource instanceof DelayedResource)) {
			resource = ((DelayedResource<?>) resource).substitute(dict);
		}

		return resource;
	}

	@Override
	public final InvocableResource getInvocableResource(Dictionary dict, String name) {
		ContextResource resource = getContextResource(dict, name);

		return resource instanceof InvocableResource ? (InvocableResource) resource : null;
	}

	@Override
	public final SubstitutableResource<?> getSubstitutableResource(Dictionary dict, String name) {
		ContextResource resource = getContextResource(dict, name);

		return resource instanceof SubstitutableResource ? (SubstitutableResource<?>) resource : null;
	}

	@Override
	public final KPSResource getKPSResource(Dictionary dict, String name) {
		ContextResource resource = getContextResource(dict, name);

		return resource instanceof KPSResource ? (KPSResource) resource : null;
	}
}
