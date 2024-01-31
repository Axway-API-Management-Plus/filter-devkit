package com.vordel.sdk.samples.quick;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.ext.filter.quick.QuickFilterField;
import com.vordel.circuit.ext.filter.quick.QuickFilterType;
import com.vordel.circuit.ext.filter.quick.QuickJavaFilterDefinition;
import com.vordel.circuit.script.context.resources.PolicyResource;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.es.Entity;
import com.vordel.trace.Trace;

@QuickFilterType(name = "JavaQuickFilter", resources = "shortcut.properties", ui = "shortcut.xml")
public class QuickFilterJavaProto extends QuickJavaFilterDefinition {
	private Selector<String> hello = null;

	private PolicyResource policy = null;

	/**
	 * This method is called when the filter is attached. The annotation is
	 * reflected in typedoc generation. individual setters are called BEFORE the
	 * attach call.
	 * 
	 * @param ctx    current config context (same as the attach call)
	 * @param entity filter instance (same as the attach call)
	 * @param field  name of the field to be set
	 */
	@QuickFilterField(name = "circuitPK", cardinality = "?", type = "^FilterCircuit")
	private void setShortcut(ConfigContext ctx, Entity entity, String field) {
		policy  = new PolicyResource(ctx, entity, field);
	}

	@QuickFilterField(name = "message", cardinality = "?", type = "string")
	private void setHelloQuickFilter(ConfigContext ctx, Entity entity, String field) {
		hello = new Selector<String>(entity.getStringValue(field), String.class);
	}

	@Override
	public void attachFilter(ConfigContext ctx, Entity entity) {
		/*
		 * The main attach call. This method is called after all individual setters has
		 * been called
		 */
	}

	@Override
	public boolean invokeFilter(Circuit c, Message m, MessageProcessor p) throws CircuitAbortException {
		if (hello != null) {
			String value = hello.substitute(m);
			
			if ((value != null) && (!value.isEmpty())) {
				Trace.info(value);
			}
		}
		
		if (policy == null) {
			throw new CircuitAbortException("No Policy Configured");
		}

		return policy.invoke(m);
	}

	@Override
	public void detachFilter() {
		/* regular detach call, no additional processing is done */
		policy = null;
		hello = null;
	}
}
