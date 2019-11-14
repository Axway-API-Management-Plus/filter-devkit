package com.vordel.circuit.script.context;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.vordel.circuit.DefaultFilter;
import com.vordel.circuit.FilterContainerImpl;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;
import com.vordel.es.EntityType;

public class ScriptInvocationContextFilter extends DefaultFilter {
	private Selector<String> attributeSelector = null;

	@Override
	public Class<? extends FilterContainerImpl> getConfigPanelClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.client.manager.filter.script.context.ScriptInvocationContextGUIFilter").asSubclass(FilterContainerImpl.class);
	}

	@Override
	public Class<? extends MessageProcessor> getMessageProcessorClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.circuit.script.context.ScriptInvocationContextProcessor").asSubclass(MessageProcessor.class);
	}

	@Override
	public void configure(ConfigContext ctx, Entity entity) throws EntityStoreException {
		super.configure(ctx, entity);

		String attributeToUse = entity.getStringValue("attributeToUse");
		Set<String> generated = new HashSet<String>();

		attributeSelector = SelectorResource.fromLiteral(attributeToUse, String.class, false);

		EntityStore es = ctx.getStore();
		EntityType resourcesType = es.getTypeForName("ScriptResource");
		Collection<ESPK> resourceList = es.listChildren(entity.getPK(), resourcesType);

		generated.add(attributeToUse);

		for (ESPK resourcePK : resourceList) {
			Entity resourceEntity = es.getEntity(resourcePK);
			String resourceName = resourceEntity.getStringValue("name");

			generated.add(attributeToUse + "." + resourceName);
		}

		addPropDefs(genProps, generated);
	}

	public Selector<String> getAttributeSelector() {
		return attributeSelector;
	}

	@Override
	public int getPossibleOutcomes() {
		return 1;
	}
}
