package com.vordel.circuit.ext.filter.quick;

import com.vordel.circuit.script.ExtendedScriptFilterProcessor;
import com.vordel.es.Entity;
import com.vordel.es.EntityType;

public class QuickScriptFilterProcessor extends ExtendedScriptFilterProcessor {
	@Override
	public String getEngineName(Entity entity) {
		EntityType type = entity.getType();

		return QuickFilterSupport.getConstantStringValue(type, QuickFilterSupport.QUICKFILTER_ENGINENAME);
	}

	@Override
	public String getEntityScript(Entity entity) {
		EntityType type = entity.getType();

		return QuickFilterSupport.getConstantStringValue(type, QuickFilterSupport.QUICKFILTER_SCRIPT);
	}
}
