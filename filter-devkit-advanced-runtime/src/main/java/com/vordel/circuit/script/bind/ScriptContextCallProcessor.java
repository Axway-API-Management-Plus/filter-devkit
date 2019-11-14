package com.vordel.circuit.script.bind;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.script.MessageAttributesLayers;
import com.vordel.circuit.script.ScriptHelper;
import com.vordel.circuit.script.context.MessageContextTracker;
import com.vordel.circuit.script.context.resources.ContextResourceProvider;
import com.vordel.circuit.script.context.resources.InvocableResource;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;
import com.vordel.trace.Trace;

public class ScriptContextCallProcessor extends InitializerShortcutProcessor {
	private boolean useInputFrame = false;
	private boolean useOutputFrame = false;

	@Override
	public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
		super.filterAttached(ctx, entity);

		this.useInputFrame = entity.getBooleanValue("useInputFrame");
		this.useOutputFrame = entity.getBooleanValue("useOutputFrame");
	}

	public final ScriptContextCallFilter getScriptContextCallFilter() {
		return (ScriptContextCallFilter) getFilter();
	}

	@Override
	public boolean invoke(Circuit c, Message m) throws CircuitAbortException {
		initialize(c, m);

		ScriptContextCallFilter filter = getScriptContextCallFilter();
		Selector<ContextResourceProvider> contextSelector = filter.getContextSelector();

		if (contextSelector == null) {
			throw new CircuitAbortException("no context selector defined");
		}

		ContextResourceProvider context = contextSelector.substitute(m);

		if (context == null) {
			throw new CircuitAbortException(String.format("requested context '%s' does not exists", contextSelector.getLiteral()));
		}

		Selector<String> invocableSelector = filter.getInvocableSelector();

		if (invocableSelector == null) {
			throw new CircuitAbortException("no invocable selector defined");
		}

		String invocableName = invocableSelector.substitute(m);

		if ((invocableName == null) || invocableName.isEmpty()) {
			throw new CircuitAbortException(String.format("Bound Invocable '%s' resolve to empty name", invocableSelector.getLiteral()));
		}

		InvocableResource resource = context.getInvocableResource(m, invocableName);
		boolean result = false;

		if (resource != null) {
			MessageContextTracker tracker = MessageContextTracker.getMessageContextTracker(m);

			if (tracker == null) {
				throw new CircuitAbortException("MessageContextTracker is not available");
			}

			Map<String, Selector<?>> inputs = filter.getInputSelectors();
			Map<String, Selector<?>> outputs = filter.getOutputSelectors();
			MessageAttributesLayers layers = tracker.getLayeredAttributes();

			/* create layer backing data */
			Map<String, Object> stack = new HashMap<String, Object>();
			Set<Object> parameters = new HashSet<Object>();
			Set<Object> whiteouts = new HashSet<Object>();

			for (Entry<String, Selector<?>> entry : inputs.entrySet()) {
				Selector<?> selector = entry.getValue();
				Object value = SelectorResource.value(selector.substitute(m));
				String name = entry.getKey();

				if (value != null) {
					stack.put(name, value);

					if (Trace.isDebugEnabled()) {
						if (value instanceof String) {
							Trace.debug(String.format("Setting Input parameter '%s' to \"%s\"", name, ScriptHelper.encodeLiteral((String) value)));
						} else if ((value instanceof Boolean) || (value instanceof Number)) {
							Trace.debug(String.format("Setting Input parameter '%s' to '%s'", name, value.toString()));
						} else {
							Trace.debug(String.format("Setting Input parameter '%s' to substituted value of '%s'", name, selector.getLiteral()));
						}
					}

					if (useInputFrame) {
						/* if user wish to remove input attributes after call */
						parameters.add(name);
					}
				} else {
					whiteouts.add(name);

					if (Trace.isDebugEnabled()) {
						Trace.debug(String.format("Input parameter '%s' has null value", name));
					}
				}
			}

			if (!useInputFrame) {
				/*
				 * weird feature, just in case user use input parameters as Copy / Modify This
				 * feature is handled here because resource call may modify input parameters
				 */
				Trace.debug("propagate Input parameters to message attributes");

				layers.putAll(stack);
				layers.keySet().removeAll(whiteouts);

				stack.clear();
				whiteouts.clear();
			}

			/*
			 * create and push a new layer. after this call any modification done by the
			 * resource will not propagate to the underlying attribute maps
			 */
			layers.push(stack, parameters, whiteouts);

			try {
				/* invoke the target resource */
				result = resource.invoke(c, m);
			} finally {
				/* compute output parameters before unstacking the current layer */
				Map<String, Object> out = new HashMap<String, Object>();

				try {
					for (Entry<String, Selector<?>> entry : outputs.entrySet()) {
						Selector<?> selector = entry.getValue();
						Object value = SelectorResource.value(selector.substitute(m));
						String name = entry.getKey();

						if (value != null) {
							out.put(name, value);

							if (Trace.isDebugEnabled()) {
								if (value instanceof String) {
									Trace.debug(String.format("Setting Output parameter '%s' to \"%s\"", name, ScriptHelper.encodeLiteral((String) value)));
								} else if ((value instanceof Boolean) || (value instanceof Number)) {
									Trace.debug(String.format("Setting Output parameter '%s' to '%s'", name, value.toString()));
								} else {
									Trace.debug(String.format("Setting Output parameter '%s' to substituted value of '%s'", name, selector.getLiteral()));
								}
							}
						} else {
							whiteouts.add(name);

							if (Trace.isDebugEnabled()) {
								Trace.debug(String.format("Output parameter '%s' has null value", name));
							}
						}
					}
				} finally {
					/*
					 * finally, remove the current layer, this puts back the attribute map in the
					 * state it had before resource invocation
					 */
					layers.pop();

					/*
					 * check if user wants to keep the attribute map. if requested, discard
					 * registered parameters, and apply layer modification to the previous layer.
					 */
					if (!useOutputFrame) {
						/* retain only live attributes */
						stack.keySet().removeAll(parameters);

						/* set generated output */
						layers.putAll(stack);

						/* apply attributes removal */
						layers.keySet().removeAll(whiteouts);
					}

					/* copy output parameters to live message */
					for (Entry<String, Object> entry : out.entrySet()) {
						String name = entry.getKey();
						Object value = entry.getValue();

						if (value != null) {
							m.put(name, value);
						} else {
							m.remove(name);
						}
					}
				}
			}
		} else {
			throw new CircuitAbortException(String.format("Resource '%s' is not invocable", invocableName));
		}

		return result;
	}
}
