package com.vordel.circuit.ext.filter.quick;

import com.vordel.circuit.script.AbstractScriptProcessor;
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
}
