package com.vordel.circuit.script.context;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.script.context.resources.ContextResource;
import com.vordel.circuit.script.context.resources.ContextResourceFactory;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;
import com.vordel.es.EntityType;
import com.vordel.trace.Trace;

public class ScriptInvocationContextProcessor extends MessageProcessor {
	private Map<String, ContextResource> resources = null;

	@Override
	public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
		super.filterAttached(ctx, entity);

		EntityStore es = ctx.getStore();
		EntityType resourcesType = es.getTypeForName("ScriptResource");
		Collection<ESPK> resourceList = es.listChildren(entity.getPK(), resourcesType);
		ContextResourceFactory factory = ContextResourceFactory.getInstance();

		Map<String, ContextResource> resources = new HashMap<String, ContextResource>();

		factory.createResources(ctx, getFilter(), resources, resourceList);

		this.resources = resources;
	}

	@Override
	public void filterDetached() {
		super.filterDetached();

		try {
			ContextResourceFactory.releaseResources(resources);
		} catch (Exception e) {
			Trace.error("Unexpected Exception when detaching filter", e);
		} finally {
			this.resources = null;
		}
	}

	@Override
	public boolean invoke(Circuit circuit, Message message) throws CircuitAbortException {
		ScriptInvocationContextFilter filter = (ScriptInvocationContextFilter) getFilter();
		Selector<String> attributeSelector = filter.getAttributeSelector();
		String attributeName = attributeSelector.substitute(message);

		if (attributeName == null) {
			throw new CircuitAbortException("Context attribute can't be null");
		}

		/* create ScriptInvocationContext */
		ScriptInvocationContext context = MessageInvocationContext.createScriptInvocationContext(resources);

		/* register ScriptInvocationContext */
		context.setMessageAttribute(message, attributeName, context);

		return true;
	}

}
