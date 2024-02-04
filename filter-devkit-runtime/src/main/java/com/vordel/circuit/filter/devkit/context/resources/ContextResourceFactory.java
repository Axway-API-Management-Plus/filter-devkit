package com.vordel.circuit.filter.devkit.context.resources;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.vordel.circuit.Filter;
import com.vordel.common.Dictionary;
import com.vordel.config.ConfigContext;
import com.vordel.dwe.DelayedESPK;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;
import com.vordel.trace.Trace;

public class ContextResourceFactory {
	private static final ContextResourceFactory INSTANCE = new ContextResourceFactory();

	public static final ContextResourceFactory getInstance() {
		return INSTANCE;
	}

	protected ContextResourceFactory() {
	}

	private static <T> SelectorResource<T> createSelectorResource(String expression, Class<T> clazz) {
		return new SelectorResource<T>(expression, clazz);
	}

	public void createResources(ConfigContext ctx, Filter owner, Map<String, ContextResource> resources, Collection<ESPK> resourceList) {
		EntityStore es = ctx.getStore();

		if (resourceList == null) {
			resourceList = Collections.emptyList();
		}

		Map<String, String> expressions = new HashMap<String, String>();

		/* first round, accumulate selector expressions */
		for (ESPK resourcePK : resourceList) {
			Entity resourceEntity = es.getEntity(resourcePK);
			String resourceType = resourceEntity.getStringValue("resourceType");
			String resourceName = resourceEntity.getStringValue("name");

			if (resourceName == null) {
				throw new EntityStoreException("Resource name can't be null");
			}

			if ("SELECTOR_RESOURCE".equals(resourceType)) {
				String expression = resourceEntity.getStringValue("selectorExpression");

				expressions.put(resourceName, expression);
			}
		}

		for (ESPK resourcePK : resourceList) {
			ContextResource resource = null;

			Entity resourceEntity = es.getEntity(resourcePK);
			String resourceType = resourceEntity.getStringValue("resourceType");
			String resourceName = resourceEntity.getStringValue("name");

			try {
				if ("SELECTOR_RESOURCE".equals(resourceType)) {
					String expression = resourceEntity.getStringValue("selectorExpression");
					String className = resourceEntity.getStringValue("selectorClazz");

					try {
						Class<?> clazz = Class.forName(className);

						resource = createSelectorResource(expression, clazz);
					} catch (ClassNotFoundException e) {
						Trace.error(String.format("Configured class '%s' does not exists", className), e);
					}
				} else if ("CONFIGURATION_RESOURCE".equals(resourceType)) {
					// ,,,AuthzCodePersist,AccessTokenPersist,OAuthAppProfile,ApiKeyProfile,BasicProfile,KerberosProfile,ProxyServer,KPSReadWriteStore,KPSDatabaseReadStore,KPSCertStore
					ESPK reference = resourceEntity.getReferenceValue("configurationReference");

					/* just to be sure that reference is not delayed */
					reference = new DelayedESPK(reference).substitute(Dictionary.empty);

					Entity referenceEntity = es.getEntity(reference);
					String referenceType = referenceEntity.getType().getName();

					if ("FilterCircuit".equals(referenceType)) {
						resource = new PolicyResource(ctx, reference);
					} else if ("Cache".equals(referenceType) || "DistributedCache".equals(referenceType)) {
						resource = new EHCacheResource(ctx, reference);
					} else if ("KPSReadWriteStore".equals(referenceType) || "KPSDatabaseReadStore".equals(referenceType) || "KPSCertStore".equals(referenceType)) {
						resource = new KPSStoreResource(ctx, reference);
					}
				}

				if (resource != null) {
					ContextResource previous = resources.put(resourceName, resource);

					if (previous != null) {
						releaseResource(previous);

						Trace.error(String.format("Multiple definition of Resource '%s'", resourceName));
					}
				} else {
					Trace.error(String.format("Resource '%s' not created", resourceName));
				}
			} catch (RuntimeException e) {
				Trace.error(String.format("Unable to create resource '%s'", resourceName), e);
			}
		}
	}

	public static void releaseResources(Map<String, ContextResource> resources) {
		if (resources != null) {
			Iterator<ContextResource> iterator = resources.values().iterator();

			while (iterator.hasNext()) {
				releaseResource(iterator.next());

				iterator.remove();
			}
		}
	}

	private static boolean releaseResource(ContextResource resource) {
		boolean released = false;

		if (resource instanceof Closeable) {
			released |= true;

			try {
				((Closeable) resource).close();
			} catch (IOException e) {
				Trace.error("Got error closing resource", e);
			}
		}

		return released;
	}
}
