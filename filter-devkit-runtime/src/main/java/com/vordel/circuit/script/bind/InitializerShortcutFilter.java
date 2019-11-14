package com.vordel.circuit.script.bind;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.vordel.circuit.DefaultFilter;
import com.vordel.circuit.DelegatingFilter;
import com.vordel.circuit.FilterContainerImpl;
import com.vordel.circuit.GlobalProperties;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;
import com.vordel.es.EntityType;
import com.vordel.trace.Trace;

public class InitializerShortcutFilter extends DefaultFilter implements DelegatingFilter {
	private Map<String, Selector<?>> outputs = null;
	private ESPK initializerPK = null;

	@Override
	public Class<? extends FilterContainerImpl> getConfigPanelClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.client.manager.filter.bind.InitializerShortcutGUIFilter").asSubclass(FilterContainerImpl.class);
	}

	@Override
	public Class<? extends MessageProcessor> getMessageProcessorClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.circuit.script.bind.InitializerShortcutProcessor").asSubclass(MessageProcessor.class);
	}

	@Override
	public void configure(ConfigContext context, Entity entity) throws EntityStoreException {
		super.configure(context, entity);

		EntityStore es = context.getStore();
		EntityType outputType = es.getTypeForName("OutputSelector");

		this.outputs = new HashMap<String, Selector<?>>();

		Collection<ESPK> outputList = es.listChildren(entity.getPK(), outputType);

		for (ESPK outputPK : outputList) {
			Entity outputEntity = es.getEntity(outputPK);
			String outputName = outputEntity.getStringValue("attribute");
			String outputCoercion = outputEntity.getStringValue("coercion");
			Class<?> outputClazz = null;

			try {
				outputClazz = Class.forName(outputCoercion);
			} catch (Exception e) {
				Trace.error("unable to retrieve coercion class, fallback to java.lang.Object", e);

				outputClazz = Object.class;
			}

			Selector<?> selector = SelectorResource.fromLiteral(outputEntity.getStringValue("selector"), outputClazz, false);

			outputs.put(outputName, selector);
		}

		addPropDefs(genProps, outputs.keySet());
	}

	public final Map<String, Selector<?>> getOutputSelectors() {
		return outputs;
	}

	@Override
	public void setEntity(Entity entity) {
		super.setEntity(entity);

		this.initializerPK = entity.getReferenceValue("contextPK");
	}

	@Override
	public ESPK getPK() {
		return getEntity().getPK();
	}

	public ESPK getInitializerPK() {
		return (initializerPK == null) || (EntityStore.ES_NULL_PK.equals(initializerPK)) ? null : initializerPK;
	}

	@Override
	public Set<ESPK> getReferencedCircuitPKs(GlobalProperties props) {
		HashSet<ESPK> pks = new HashSet<ESPK>();
		ESPK initializerPK = getInitializerPK();

		if (initializerPK != null) {
			pks.add(initializerPK);
		}

		return pks;
	}

	@Override
	public void updateRefs(ConfigContext ctx) {
	}
}
