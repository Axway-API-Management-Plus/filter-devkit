package com.vordel.circuit.script.bind;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.script.MessageAttributesLayers;
import com.vordel.circuit.script.ScriptHelper;
import com.vordel.circuit.script.context.MessageContextTracker;
import com.vordel.circuit.script.context.resources.PolicyResource;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.common.Dictionary;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.dwe.DelayedESPK;
import com.vordel.el.Selector;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;
import com.vordel.trace.Trace;

public class InitializerShortcutProcessor extends MessageProcessor {
	private PolicyResource initializer = null;

	@Override
	public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
		super.filterAttached(ctx, entity);

		InitializerShortcutFilter filter = getInitializerShortcutFilter();
		ESPK initializerPK = filter.getInitializerPK();

		if (initializerPK != null) {
			/* just to be sure that reference is not delayed */
			initializerPK = new DelayedESPK(initializerPK).substitute(Dictionary.empty);
		}

		this.initializer = initializerPK == null ? null : new PolicyResource(ctx, initializerPK);
	}

	public InitializerShortcutFilter getInitializerShortcutFilter() {
		return (InitializerShortcutFilter) getFilter();
	}

	@Override
	public void filterDetached() {
		super.filterDetached();

		this.initializer = null;
	}

	public final void initialize(Circuit c, Message m) throws CircuitAbortException {
		if (initializer == null) {
			Trace.debug("no initializer policy configured");
		} else {
			MessageContextTracker tracker = MessageContextTracker.getMessageContextTracker(m);

			if (tracker == null) {
				throw new CircuitAbortException("MessageContextTracker is not available");
			}

			Set<ESPK> initializers = tracker.getInitializers();

			if (initializers.add(initializer.getCircuitPK())) {
				/* Initializer has not been called yet */
				MessageAttributesLayers layers = tracker.getLayeredAttributes();

				/* push globals as top layer */
				layers.push(layers.first());

				try {
					if (Trace.isDebugEnabled()) {
						Trace.debug(String.format("Calling initializer policy '%s'", initializer.getCircuit().getName()));
					}

					if (!initializer.invoke(c, m)) {
						throw new CircuitAbortException("Context initializer returned false");
					}
				} finally {
					layers.pop();
				}
			} else if (Trace.isDebugEnabled()) {
				Trace.debug(String.format("Not calling policy '%s' (initializer policy already called)", initializer.getCircuit().getName()));
			}
		}
	}
	
	public boolean invoke(Circuit c, Message m) throws CircuitAbortException {
		InitializerShortcutFilter filter = getInitializerShortcutFilter();
		Map<String, Selector<?>> outputs = filter.getOutputSelectors();

		initialize(c, m);

		for (Entry<String, Selector<?>> entry : outputs.entrySet()) {
			Selector<?> selector = entry.getValue();
			Object value = SelectorResource.value(selector.substitute(m));
			String name = entry.getKey();

			if (value != null) {
				m.put(name, value);

				if (Trace.isDebugEnabled()) {
					if (value instanceof String) {
						Trace.debug(String.format("Setting attribute '%s' to \"%s\"", name, ScriptHelper.encodeLiteral((String) value)));
					} else if ((value instanceof Boolean) || (value instanceof Number)) {
						Trace.debug(String.format("Setting attribute '%s' to '%s'", name, value.toString()));
					} else {
						Trace.debug(String.format("Setting attribute '%s' to substituted value of '%s'", name, selector.getLiteral()));
					}
				}
			} else {
				m.remove(name);

				if (Trace.isDebugEnabled()) {
					Trace.debug(String.format("Removing attribute '%s' (no value returned by expression)", name));
				}
			}
		}

		return true;
	}
}
