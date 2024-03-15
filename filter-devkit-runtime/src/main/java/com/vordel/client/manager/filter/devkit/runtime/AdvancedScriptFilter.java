package com.vordel.client.manager.filter.devkit.runtime;

import java.util.Collection;
import java.util.Set;

import com.vordel.circuit.DefaultFilter;
import com.vordel.circuit.FilterContainerImpl;
import com.vordel.circuit.MessageProcessor;
import com.vordel.common.util.PropDef;
import com.vordel.config.ConfigContext;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;

public class AdvancedScriptFilter extends DefaultFilter implements VordelLegacyFilter {
	@Override
	public Class<? extends MessageProcessor> getMessageProcessorClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.circuit.filter.devkit.script.advanced.AdvancedScriptProcessor").asSubclass(MessageProcessor.class);
	}

	@Override
	public Class<? extends FilterContainerImpl> getConfigPanelClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.client.manager.filter.devkit.studio.script.advanced.AdvancedScriptGUIFilter").asSubclass(FilterContainerImpl.class);
	}
	
	@Override
	public void configure(ConfigContext context, Entity entity) throws EntityStoreException {
		super.configure(context, entity);

		addPropsDefs(reqProps, entity, "requiredProperties");
		addPropsDefs(genProps, entity, "generatedProperties");
		addPropsDefs(consProps, entity, "consumedProperties");
	}
	
	private void addPropsDefs(Set<PropDef> propDefs, Entity entity, String key) {
		if (entity.containsKey(key)) {
			Collection<String> props = entity.getStringValues(key);

			addPropDefs(propDefs, props);
		}
	}
}
