package com.vordel.circuit.groovy;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.vordel.circuit.DefaultFilter;
import com.vordel.circuit.FilterContainerImpl;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.common.Dictionary;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;
import com.vordel.es.EntityType;

public class GroovyScriptFilter extends DefaultFilter {
	private Selector<String> exportSelector = null;

	@Override
	public Class<? extends FilterContainerImpl> getConfigPanelClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.client.manager.filter.groovy.GroovyScriptGUIFilter").asSubclass(FilterContainerImpl.class);
	}

	@Override
	public Class<? extends MessageProcessor> getMessageProcessorClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.circuit.groovy.GroovyScriptProcessor").asSubclass(MessageProcessor.class);
	}

	@Override
	public void configure(ConfigContext ctx, Entity entity) throws EntityStoreException {
		super.configure(ctx, entity);

		exportSelector = SelectorResource.fromLiteral(entity.getStringValue("exportAttribute"), String.class, false);
		String attributeToUse = exportSelector == null ? null : exportSelector.substitute(Dictionary.empty);

		if ((attributeToUse != null) && (!attributeToUse.isEmpty())) {
			Set<String> generated = new HashSet<String>();

			EntityStore es = ctx.getStore();
			EntityType resourcesType = es.getTypeForName("ScriptResource");
			Collection<ESPK> resourceList = es.listChildren(entity.getPK(), resourcesType);

			generated.add(attributeToUse);

			for (ESPK resourcePK : resourceList) {
				Entity resourceEntity = es.getEntity(resourcePK);
				String resourceName = resourceEntity.getStringValue("name");

				generated.add(attributeToUse + "." + resourceName);
			}

			addPropDefs(genProps, generated);
		}

		/*
		 * retrieve script kind (direct invocation, shared method only, or web service)
		 */
		String scriptKind = entity.getStringValue("scriptKind");

		if (GroovyScriptProcessor.SCRIPT_INVOKE.equals(scriptKind)) {
			/*
			 * If script is a regular executable script, apply required/generated/consumed attributes.
			 */
			// XXX should disable attributes tab if script is not executable
			if (entity.containsKey("requiredProperties")) {
				Collection<String> consumedProps = entity.getStringValues("requiredProperties");

				addPropDefs(reqProps, consumedProps);
			}
			if (entity.containsKey("generatedProperties")) {
				Collection<String> consumedProps = entity.getStringValues("generatedProperties");

				addPropDefs(genProps, consumedProps);
			}
			if (entity.containsKey("consumedProperties")) {
				Collection<String> consumedProps = entity.getStringValues("consumedProperties");

				addPropDefs(consProps, consumedProps);
			}
		} else if (GroovyScriptProcessor.SCRIPT_JAXRS.equals(scriptKind)) {
			/*
			 * If script is a JAX-RS service add required attribute for service.
			 */
			// XXX body is marqued as requested, but it applies according to request VERB on runtime
			Set<String> required = new HashSet<String>();

			required.add(MessageProperties.HTTP_HEADERS);
			required.add(MessageProperties.HTTP_REQ_VERB);
			required.add(MessageProperties.CONTENT_BODY);
			required.add(MessageProperties.HTTP_REQ_URI);
			required.add(MessageProperties.HTTP_REQ_PROTOCOL);
			required.add(MessageProperties.HTTP_REQ_HOSTNAME);

			addPropDefs(reqProps, required);
		}
	}

	public Selector<String> getExportSelector() {
		return exportSelector;
	}
}
