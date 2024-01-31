package com.vordel.circuit.ext.filter.quick;

import java.util.Arrays;

import com.vordel.circuit.DefaultFilter;
import com.vordel.circuit.FilterContainerImpl;
import com.vordel.circuit.VordelLegacyFilter;
import com.vordel.config.ConfigContext;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;
import com.vordel.es.EntityType;

public abstract class AbstractQuickFilter extends DefaultFilter implements VordelLegacyFilter {
	@Override
	public Class<? extends FilterContainerImpl> getConfigPanelClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.client.ext.filter.quick.QuickFilterGUI").asSubclass(FilterContainerImpl.class);
	}

	@Override
	public void configure(ConfigContext context, Entity entity) throws EntityStoreException {
		super.configure(context, entity);
		
		EntityType type = entity.getType();
		
		String[] required = QuickFilterSupport.getConstantStringValues(type, QuickFilterSupport.QUICKFILTER_REQUIRED, true);
		String[] consumed = QuickFilterSupport.getConstantStringValues(type, QuickFilterSupport.QUICKFILTER_CONSUMED, true);
		String[] generated = QuickFilterSupport.getConstantStringValues(type, QuickFilterSupport.QUICKFILTER_GENERATED, true);
		
		if (required != null) {
			addPropDefs(reqProps, Arrays.asList(required));
		}
		
		if (consumed != null) {
			addPropDefs(consProps, Arrays.asList(consumed));
		}
		
		if (generated != null) {
			addPropDefs(genProps, Arrays.asList(generated));
		}
	}
}
