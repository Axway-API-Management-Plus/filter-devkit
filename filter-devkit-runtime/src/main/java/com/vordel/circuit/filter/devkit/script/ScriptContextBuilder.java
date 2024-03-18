package com.vordel.circuit.filter.devkit.script;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.script.ScriptException;

import org.codehaus.groovy.runtime.MethodClosure;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.filter.devkit.context.ExtensionResourceProvider;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionFunction;
import com.vordel.circuit.filter.devkit.context.annotations.InvocableMethod;
import com.vordel.circuit.filter.devkit.context.annotations.SubstitutableMethod;
import com.vordel.circuit.filter.devkit.context.resources.AbstractContextResourceProvider;
import com.vordel.circuit.filter.devkit.context.resources.ContextResource;
import com.vordel.circuit.filter.devkit.context.resources.ContextResourceProvider;
import com.vordel.circuit.filter.devkit.context.resources.EHCacheResource;
import com.vordel.circuit.filter.devkit.context.resources.KPSStoreResource;
import com.vordel.circuit.filter.devkit.context.resources.PolicyResource;
import com.vordel.circuit.filter.devkit.context.resources.SelectorResource;
import com.vordel.common.Dictionary;
import com.vordel.config.Circuit;
import com.vordel.dwe.DelayedESPK;
import com.vordel.dwe.Service;
import com.vordel.el.Selector;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.util.ShorthandKeyFinder;
import com.vordel.es.xes.PortableESPKFactory;
import com.vordel.kps.Store;
import com.vordel.precipitate.SolutionPack;

import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;

/**
 * runtime builder for regular api gateway scripts. Allows to create a
 * ScriptContext object which references policies, KPS and caches. It also
 * include groovy script instance reflection as well as class static methods
 * reflection.
 * 
 * @author rdesaintleger@axway.com
 */
public final class ScriptContextBuilder {
	private final Map<String, ContextResource> resources;

	/**
	 * public constructor with actual resources map. This way the advanced script
	 * framework can inherit this implementation.
	 * 
	 * @param resources current script's set of resources
	 */
	public ScriptContextBuilder(Map<String, ContextResource> resources) {
		this.resources = resources;
	}

	/**
	 * Check if the name parameter is not null
	 * 
	 * @param name
	 * @throws ScriptException
	 */
	private static final void checkName(String name) throws ScriptException {
		if (name == null) {
			throw new ScriptException("name parameter cannot be null");
		}
	}

	/**
	 * reflect exported methods of a groovy script.
	 * 
	 * @param script script to be reflected.
	 */
	private void reflectGroovyScript(Script script) {
		if (script != null) {
			/* reflect script instance resources */
			ExtensionResourceProvider.reflectInstance(resources, script);
		}
	}

	/**
	 * Attach a KPS resource using an alias. Used only for context creation without
	 * Advanced Script Filter reference binding.
	 * 
	 * @param name  name of resource to be created
	 * @param alias KPS Table alias.
	 * @return this instance of builder
	 * @throws ScriptException if name or alias are missing.
	 */
	public ScriptContextBuilder attachKPSResourceByAlias(String name, String alias) throws ScriptException {
		checkName(name);

		if (alias == null) {
			throw new ScriptException("alias parameter cannot be null");
		}

		Store store = KPSStoreResource.getStoreByAlias(alias);

		if (store == null) {
			throw new ScriptException(String.format("KPS Store '%s' does not exists", alias));
		}

		KPSStoreResource resource = new KPSStoreResource(store);

		resources.put(name, resource);

		return this;
	}

	/**
	 * Attach a Cache resource using configured name. Used only for context creation
	 * without Advanced Script Filter reference binding.
	 * 
	 * @param name      name of resource to be created
	 * @param cacheName Cache configured name.
	 * @return this instance of builder
	 * @throws ScriptException if name or configured cache name are missing.
	 */
	public ScriptContextBuilder attachCacheResourceByName(String name, String cacheName) throws ScriptException {
		checkName(name);

		if (cacheName == null) {
			throw new ScriptException("cache name parameter cannot be null");
		}

		try {
			EHCacheResource.getCache(cacheName);
		} catch (CircuitAbortException e) {
			ScriptException error = new ScriptException(String.format("cache '%s' does not exists", cacheName));

			error.initCause(e);

			throw error;
		}

		EHCacheResource resource = new EHCacheResource(cacheName);

		resources.put(name, resource);

		return this;
	}

	/**
	 * Attach a selector resource using expression and coerced type.
	 * 
	 * @param <T>        Type parameter locked to coerced selector type
	 * @param name       name of resource to be created
	 * @param expression selector expression
	 * @param clazz      coerced type for selector substitution
	 * @return this instance of builder
	 * @throws ScriptException if any parameter is missing.
	 */
	public <T> ScriptContextBuilder attachSelectorResourceByExpression(String name, String expression, Class<T> clazz) throws ScriptException {
		checkName(name);

		if (expression == null) {
			throw new ScriptException("expression parameter cannot be null");
		}

		if (clazz == null) {
			throw new ScriptException("class parameter cannot be null");
		}

		Selector<T> selector = SelectorResource.fromExpression(expression, clazz);

		resources.put(name, new SelectorResource<T>(selector));

		return this;
	}

	/**
	 * Attach a Policy resource using its shorthand key. Used only for context
	 * creation without Advanced Script Filter reference binding.
	 * 
	 * @param name name of resource to be created
	 * @param key  policy shorthand key
	 * @return this instance of builder
	 * @throws ScriptException if any parameter is missing or if key does not
	 *                         resolves to anything.
	 */
	public ScriptContextBuilder attachPolicyByShorthandKey(String name, String key) throws ScriptException {
		checkName(name);

		if (key == null) {
			throw new ScriptException("portable ESPK parameter cannot be null");
		}

		Service service = Service.getInstance();
		EntityStore es = service.getStore();
		Entity entity = new ShorthandKeyFinder(es).getEntity(key);

		if (entity == null) {
			throw new ScriptException("shorthand key is not valid");
		}

		return attachPolicyByESPK(name, entity.getPK());
	}

	/**
	 * Attach a Policy resource using its xml reference. Used only for context
	 * creation without Advanced Script Filter reference binding.
	 * 
	 * @param name name of resource to be created
	 * @param key  policy xml reference
	 * @return this instance of builder
	 * @throws ScriptException if any parameter is missing or if xml reference does
	 *                         not resolves to anything.
	 */
	public ScriptContextBuilder attachPolicyByPortableESPK(String name, String key) throws ScriptException {
		checkName(name);

		if (key == null) {
			throw new ScriptException("portable ESPK parameter cannot be null");
		}

		PortableESPKFactory factory = PortableESPKFactory.newInstance();

		return attachPolicyByESPK(name, factory.createPortableESPK(key));
	}

	/**
	 * Internal entry point for policy resource binding. called with resolved
	 * reference. Used only for context creation without Advanced Script Filter
	 * reference binding.
	 * 
	 * @param name        name of resource to be created
	 * @param delegatedPK policy xml reference
	 * @return this instance of builder
	 * @throws ScriptException if any parameter is missing.
	 */
	private ScriptContextBuilder attachPolicyByESPK(String name, ESPK delegatedPK) throws ScriptException {
		if (delegatedPK == null) {
			throw new ScriptException("portable key is not valid");
		}

		DelayedESPK delayedPK = delegatedPK instanceof DelayedESPK ? (DelayedESPK) delegatedPK : new DelayedESPK(delegatedPK);
		ESPK circuitPK = delayedPK.substitute(Dictionary.empty);
		Service service = Service.getInstance();
		Circuit circuit = null;

		if (EntityStore.ES_NULL_PK.equals(circuitPK)) {
			circuitPK = null;
		} else {
			SolutionPack config = service.getLocalPack();

			circuit = config.getCircuit(circuitPK);
		}

		PolicyResource resource = new PolicyResource(circuit, circuitPK);

		resources.put(name, resource);

		return this;
	}

	/**
	 * Reflect existing loaded class for static method exports if
	 * {@link InvocableMethod}, {@link SubstitutableMethod} or
	 * {@link ExtensionFunction}
	 * 
	 * @param name  name of resource to be created
	 * @param clazz class to be reflected
	 * @return this instance of builder
	 * @throws ScriptException if any parameter is missing.
	 */
	public ScriptContextBuilder reflectClass(String name, Class<?> clazz) throws ScriptException {
		checkName(name);

		if (clazz == null) {
			throw new ScriptException("class parameter cannot be null");
		}

		ExtensionResourceProvider.reflectClass(resources, clazz);

		return this;
	}

	/**
	 * Static entry point for reflecting an instance of a Groovy Script.
	 * 
	 * @param script existing groovy script.
	 */
	public static final void bindGroovyScriptContext(Script script) {
		bindGroovyScriptContext(script, null);
	}

	/**
	 * Static entry point for creating a ScriptContext object. Use a callback to add
	 * resources
	 * 
	 * @param configurator callback which will be applied to add resource to the
	 *                     resulting context.
	 * @return a {@link ScriptContext} object containing resources created using the
	 *         configurator callback
	 */
	public static final ScriptContext createScriptContext(Consumer<ScriptContextBuilder> configurator) {
		Map<String, ContextResource> resources = new HashMap<String, ContextResource>();
		ScriptContextBuilder builder = new ScriptContextBuilder(resources);

		if (configurator != null) {
			configurator.accept(builder);
		}

		return new ScriptContextAdapter(resources);
	}

	/**
	 * Static entry point for creating a ScriptContext object from exported
	 * resources of a Groovy Script and a configurator callback
	 * 
	 * @param script       existing groovy script to be reflected
	 * @param configurator callback which will be applied to add resource to the
	 *                     resulting context.
	 */
	public static final void bindGroovyScriptContext(Script script, Consumer<ScriptContextBuilder> configurator) {
		/* check if calling from policy studio */
		Service service = Service.getInstance();

		if ((script != null) && (service != null)) {
			Closure<?> attach = getGroovyClosure(script, "attachResources");
			Closure<?> reflect = getGroovyClosure(script, "reflectResources");

			if ((attach != null) && (reflect != null)) {
				/* keep compatibility with full install */
				((Closure<?>) reflect).invokeMethod("reflectResources", script);
				((Closure<?>) attach).invokeMethod("attachResources", configurator);
			} else {
				/* create runtime context */
				Map<String, ContextResource> resources = new HashMap<String, ContextResource>();
				ScriptContextBuilder builder = new ScriptContextBuilder(resources);
				ScriptContextAdapter runtime = new ScriptContextAdapter(resources);

				/* add runtime closures to script */
				bindGroovyClosures(script, runtime, ScriptRuntime.class.getDeclaredMethods());

				/* reflect groovy script for runtime */
				builder.reflectGroovyScript(script);

				if (configurator != null) {
					/* and apply configurator closure */
					configurator.accept(builder);
				}
			}
		}
	}

	private static final void bindGroovyClosures(Script script, ScriptContext context, Method[] methods) {
		for (Method method : methods) {
			String name = method.getName();
			MethodClosure closure = new MethodClosure(context, name);

			script.setProperty(name, closure);
		}
	}

	private static final Closure<?> getGroovyClosure(Script script, String name) {
		try {
			Object closure = script.getProperty("attachResources");

			return closure instanceof Closure ? (Closure<?>) closure : null;
		} catch (MissingPropertyException e) {
			// ignore
		}

		return null;
	}

	private static final class ScriptContextAdapter extends ScriptContext {
		private final Map<String, ContextResource> resources;
		private final ContextResourceProvider exports = new AbstractContextResourceProvider() {
			@Override
			public ContextResource getContextResource(String name) {
				return resources.get(name);
			}
		};

		private ScriptContextAdapter(Map<String, ContextResource> resources) {
			this.resources = resources;
		}

		@Override
		public ContextResource getContextResource(String name) {
			return resources.get(name);
		}

		@Override
		public ContextResourceProvider getExportedResources() {
			return exports;
		}
	}
}