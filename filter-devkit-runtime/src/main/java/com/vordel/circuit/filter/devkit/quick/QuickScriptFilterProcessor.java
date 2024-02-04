package com.vordel.circuit.filter.devkit.quick;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterSupport;
import com.vordel.circuit.filter.devkit.script.advanced.AbstractScriptProcessor;
import com.vordel.client.manager.filter.devkit.runtime.AbstractQuickFilter;
import com.vordel.config.Circuit;
import com.vordel.es.Entity;
import com.vordel.es.EntityType;

public class QuickScriptFilterProcessor extends AbstractScriptProcessor {
	@Override
	protected String getEngineName(Entity entity) {
		EntityType type = entity.getType();

		return AbstractQuickFilter.getConstantStringValue(type, QuickFilterSupport.QUICKFILTER_ENGINENAME);
	}

	@Override
	protected String getEntityScript(Entity entity) {
		EntityType type = entity.getType();

		return AbstractQuickFilter.getConstantStringValue(type, QuickFilterSupport.QUICKFILTER_SCRIPT);
	}

	@Override
	protected Object invokeScript(Circuit c, Message m) throws CircuitAbortException {
		/* override to have filter like call */
		return invokeScript(c, m, true, true);
	}
}