package com.vordel.circuit.script.bind;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.vordel.circuit.FilterContainerImpl;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.script.context.resources.ContextResourceProvider;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.common.util.PropDef;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;
import com.vordel.es.EntityType;
import com.vordel.trace.Trace;

public class ScriptContextCallFilter extends InitializerShortcutFilter {
	private Selector<ContextResourceProvider> contextSelector = null;
	private Selector<String> invocableSelector = null;

	private Map<String, Selector<?>> inputs = null;

	@Override
	public Class<? extends FilterContainerImpl> getConfigPanelClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.client.manager.filter.bind.ScriptContextCallGUIFilter").asSubclass(FilterContainerImpl.class);
	}

	@Override
	public Class<? extends MessageProcessor> getMessageProcessorClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.circuit.script.bind.ScriptContextCallProcessor").asSubclass(MessageProcessor.class);
	}

	@Override
	public void configure(ConfigContext context, Entity entity) throws EntityStoreException {
		super.configure(context, entity);

		EntityStore es = context.getStore();
		EntityType inputType = es.getTypeForName("InputSelector");
		String invocableExpression = entity.getStringValue("contextInvocable");
		String contextExpression = entity.getStringValue("contextSelector");

		this.invocableSelector = null;
		this.contextSelector = null;

		if ((contextExpression != null) && (!contextExpression.isEmpty())) {
			this.contextSelector = SelectorResource.fromLiteral(contextExpression, ContextResourceProvider.class, true);
		}

		if ((invocableExpression != null) && (!invocableExpression.isEmpty())) {
			this.invocableSelector = SelectorResource.fromLiteral(invocableExpression, String.class, false);
		}

		this.inputs = new HashMap<String, Selector<?>>();

		Collection<ESPK> inputList = es.listChildren(entity.getPK(), inputType);
		Set<String> required = new HashSet<String>();

		for (ESPK inputPK : inputList) {
			Entity inputEntity = es.getEntity(inputPK);
			String inputName = inputEntity.getStringValue("attribute");
			String inputCoercion = inputEntity.getStringValue("coercion");
			Class<?> inputClazz = null;

			try {
				inputClazz = Class.forName(inputCoercion);
			} catch (Exception e) {
				Trace.error("unable to retrieve coercion class, fallback to java.lang.Object", e);

				inputClazz = Object.class;
			}

			Selector<?> selector = SelectorResource.fromLiteral(inputEntity.getStringValue("selector"), inputClazz, false);

			if (selector.containsWildcards()) {
				for (String key : selector.getWildcardRefs()) {
					if (!ignoreField(key)) {
						required.add(key);
					}
				}
			}

			inputs.put(inputName, selector);
		}

		addPropDefs(reqProps, required);

		Selector<ContextResourceProvider> contextSelector = getContextSelector();

		/* assume context selector is fully resolved */
		if ((contextSelector != null) && (contextSelector.containsWildcards())) {
			for (String key : contextSelector.getWildcardRefs()) {
				Iterator<PropDef> iterator = reqProps.iterator();

				while (iterator.hasNext()) {
					if (key.equals(iterator.next().getName())) {
						iterator.remove();
					}
				}
			}
		}
	}

	public final Selector<ContextResourceProvider> getContextSelector() {
		return contextSelector;
	}

	public final Selector<String> getInvocableSelector() {
		return invocableSelector;
	}

	public final Map<String, Selector<?>> getInputSelectors() {
		return inputs;
	}
}
