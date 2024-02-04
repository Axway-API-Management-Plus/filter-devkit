package com.vordel.client.manager.filter.devkit.runtime;

import java.util.Arrays;

import com.vordel.circuit.DefaultFilter;
import com.vordel.circuit.FilterContainerImpl;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterSupport;
import com.vordel.config.ConfigContext;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;
import com.vordel.es.EntityType;
import com.vordel.es.Field;
import com.vordel.es.Value;

public abstract class AbstractQuickFilter extends DefaultFilter implements VordelLegacyFilter {
	@Override
	public Class<? extends FilterContainerImpl> getConfigPanelClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.client.manager.filter.devkit.studio.quick.QuickFilterGUI").asSubclass(FilterContainerImpl.class);
	}

	@Override
	public void configure(ConfigContext context, Entity entity) throws EntityStoreException {
		super.configure(context, entity);
		
		EntityType type = entity.getType();
		
		String[] required = AbstractQuickFilter.getConstantStringValues(type, QuickFilterSupport.QUICKFILTER_REQUIRED, true);
		String[] consumed = AbstractQuickFilter.getConstantStringValues(type, QuickFilterSupport.QUICKFILTER_CONSUMED, true);
		String[] generated = AbstractQuickFilter.getConstantStringValues(type, QuickFilterSupport.QUICKFILTER_GENERATED, true);
		
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

	public static String getConstantStringValue(EntityType entity, String name) {
		String result = null;
	
		if (entity != null) {
			Field clazz = entity.getConstantField(name);
	
			if (clazz != null) {
				Value[] values = clazz.getValues();
	
				if ((values != null) && (values.length == 1)) {
					Value value = values[0];
					Object data = value == null ? null : value.getData();
	
					if (data instanceof String) {
						result = (String) data;
					}
				}
			}
		}
	
		return result;
	}

	public static String[] getConstantStringValues(EntityType entity, String name, boolean trim) {
		return QuickFilterSupport.splitValues(getConstantStringValue(entity, name), trim);
	}
}
