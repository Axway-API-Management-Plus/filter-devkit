package com.vordel.client.manager.filter.devkit.runtime;

import java.util.Arrays;

import com.vordel.circuit.DefaultFilter;
import com.vordel.circuit.FilterContainerImpl;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterConsumed;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterGenerated;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterRequired;
import com.vordel.config.ConfigContext;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;

public abstract class JavaQuickFilter extends DefaultFilter implements VordelLegacyFilter {
	@Override
	public abstract Class<? extends FilterContainerImpl> getConfigPanelClass() throws ClassNotFoundException;

	@Override
	public abstract Class<? extends MessageProcessor> getMessageProcessorClass() throws ClassNotFoundException;

	@Override
	public void configure(ConfigContext ctx, Entity entity) throws EntityStoreException {
		super.configure(ctx, entity);
		
		Class<?> filterClass = getClass();
		QuickFilterRequired required = filterClass.getAnnotation(QuickFilterRequired.class);
		QuickFilterConsumed consumed = filterClass.getAnnotation(QuickFilterConsumed.class);
		QuickFilterGenerated generated = filterClass.getAnnotation(QuickFilterGenerated.class);
		
		if (required != null) {
			addPropDefs(reqProps, Arrays.asList(required.value()));
		}
		
		if (consumed != null) {
			addPropDefs(consProps, Arrays.asList(consumed.value()));
		}
		
		if (generated != null) {
			addPropDefs(genProps, Arrays.asList(generated.value()));
		}
	}
}
