package com.vordel.circuit.filter.devkit.eval;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.filter.devkit.context.resources.SelectorResource;
import com.vordel.circuit.filter.devkit.quick.JavaQuickFilterDefinition;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterField;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterType;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.es.Entity;
import com.vordel.trace.Trace;

@QuickFilterType(name = "ExtendedSelectorFilter", icon = "eval_expression", category = "utility", resources = "extended_eval.properties", page = "extended_eval.xml")
public class ExtendedSelectorFilter extends JavaQuickFilterDefinition {
	private Selector<Boolean> selector;

	@QuickFilterField(name = "expression", cardinality = "1", type = "string", defaults = "${1 + 1 == 2}")
	private void setLoopCondition(ConfigContext ctx, Entity entity, String field) {
		this.selector = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}


	@Override
	public boolean invokeFilter(Circuit c, Message m, MessageProcessor p) throws CircuitAbortException {
		Boolean rc = SelectorResource.invoke(m, selector);

		if (Trace.isDebugEnabled()) {
			Trace.debug(String.format("evaluate %s = %s", selector, rc));
		}

		return rc;
	}

	@Override
	public void attachFilter(ConfigContext ctx, Entity entity) {
	}

	@Override
	public void detachFilter() {
	}

}
