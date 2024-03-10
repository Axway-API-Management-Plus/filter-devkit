package com.vordel.circuit.filter.devkit.script.advanced;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.ScriptException;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.context.ExtensionContext;
import com.vordel.circuit.filter.devkit.context.ExtensionLoader;
import com.vordel.circuit.filter.devkit.context.resources.AbstractContextResourceProvider;
import com.vordel.circuit.filter.devkit.context.resources.CacheResource;
import com.vordel.circuit.filter.devkit.context.resources.ContextResource;
import com.vordel.circuit.filter.devkit.context.resources.ContextResourceFactory;
import com.vordel.circuit.filter.devkit.context.resources.ContextResourceProvider;
import com.vordel.circuit.filter.devkit.context.resources.InvocableResource;
import com.vordel.circuit.filter.devkit.context.resources.KPSResource;
import com.vordel.common.Dictionary;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;
import com.vordel.es.EntityType;
import com.vordel.trace.Trace;

import groovy.lang.Script;

/**
 * Implementation of the Advanced Script Processor. Script coming from the
 * original script filter are compatible. (substitution on deploy time,
 * CircuitAbortException wrapped in ScriptException, attach() and detach() are
 * set as optional and will not trigger an exception if missing.
 * 
 * @author rdesaintleger@axway.com
 */
public class AdvancedScriptProcessor extends AbstractScriptProcessor {
	/**
	 * Resources retrieved from configured UI
	 */
	private Map<String, ContextResource> resources = null;

	/**
	 * this boolean indicated if the filter is attached (configuration functions
	 * throws exceptions if true)
	 */
	private boolean attached = false;
	/**
	 * if true, calls invoke(circuit, message) instead of invoke(message)
	 */
	private boolean extendedInvoke = false;
	/**
	 * unwrap CircuitAbortExceptions
	 */
	private boolean unwrapCircuitAbortException = false;

	/**
	 * Instance of reflected groovy script
	 */
	private Script groovyInstance = null;
	/**
	 * Detach method of groovy script (if compatible with argument injection)
	 */
	private Method groovyDetach = null;
	/**
	 * Invoke method of groovy script (if compatible with argument injection)
	 */
	private Method groovyInvoke = null;

	/**
	 * exportable resource provider
	 */
	private final ExportedResources exports = new ExportedResources();

	@Override
	protected String substituteScript(String script) {
		/* apply the same behavior that we have in the default Scripting Filter */
		Selector<String> wild = new Selector<String>(script, String.class);

		return wild.substitute(Dictionary.empty);
	}

	@Override
	public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
		try {
			super.filterAttached(ctx, entity);
		} finally {
			attached = true;
		}
	}

	@Override
	public void filterDetached() {
		Object[] args = (groovyInstance == null) || (groovyDetach == null) ? null : getGroovyDetachArguments(groovyDetach);

		if (args != null) {
			try {
				/* invoke the groovy detach method */
				groovyDetach.invoke(groovyInstance, args);
			} catch (InvocationTargetException e) {
				Throwable cause = e.getCause();

				Trace.error("Unexpected exception during script detachment", cause);
			} catch (Exception e) {
				Trace.error("Unable to run script detach function", e);
			}
		} else {
			try {
				detachScript();
			} catch (NoSuchMethodException e) {
				/* ignore, detach is optional */
			}
		}

		try {
			ContextResourceFactory.releaseResources(resources);
		} catch (Exception e) {
			Trace.error("Unexpected Exception when releasing resources", e);
		} finally {
			this.resources = null;
		}
	}

	protected void attachResources(ConfigContext ctx, Entity entity, Map<String, ContextResource> resources) {
		EntityStore es = ctx.getStore();
		EntityType resourcesType = es.getTypeForName("ScriptResource");
		Collection<ESPK> resourceList = es.listChildren(entity.getPK(), resourcesType);
		ContextResourceFactory factory = ContextResourceFactory.getInstance();

		/* create resources */
		factory.createResources(ctx, getFilter(), resources, resourceList);
	}

	@Override
	protected void attachScript(ConfigContext ctx, Entity entity) throws ScriptException {
		try {
			super.attachScript(ctx, entity);
		} catch (NoSuchMethodException ex) {
			/* ignore, attach is optional */
		}
	}

	@Override
	protected void evaluateScript(ConfigContext ctx, Entity entity, String script) throws ScriptException {
		/* retrieve resources */
		attachResources(ctx, entity, resources = new HashMap<String, ContextResource>());

		/* create the runtime object used to create function closures */
		ExportedRuntime runtime = new ExportedRuntime();
		AdvancedScriptRuntimeBinder binder = AdvancedScriptRuntimeBinder.getScriptBinder(engine);

		if (binder != null) {
			/* create top level closure from local runtime */
			binder.bind(engine, runtime);
		}

		/*
		 * after resources has been bound and script runtime loaded, execute the script
		 */
		super.evaluateScript(ctx, entity, script);
	}

	@Override
	protected Object invokeScript(Circuit c, Message m) throws CircuitAbortException {
		Object[] args = (groovyInstance == null) || (groovyInvoke == null) ? null : getGroovyInvokeArguments(groovyInvoke, c, m);
		Object result = null;

		if (args != null) {
			try {
				/* invoke the groovy method */
				result = groovyInvoke.invoke(groovyInstance, args);
			} catch (InvocationTargetException e) {
				/* got exception invoking script, examine cause */
				Throwable cause = e.getCause();

				if (cause instanceof CircuitAbortException) {
					/* just relay this king of exception */
					throw (CircuitAbortException) cause;
				} else if (cause instanceof ScriptException) {
					if (unwrapCircuitAbortException) {
						/*
						 * in case of ScriptException, try to unwrap it according to requested
						 * configuration
						 */
						CircuitAbortException nested = unwrapException((ScriptException) cause, CircuitAbortException.class);

						if (nested != null) {
							throw nested;
						}
					}

					throw new CircuitAbortException(cause);
				} else {
					/*
					 * in all other cases (including runtime exceptions, report unexpected exception
					 */
					throw new CircuitAbortException("Unexpected exception during groovy invocation", cause);
				}
			} catch (Exception e) {
				/* fallback case for invocation */
				throw new CircuitAbortException("Unable to run groovy invoke function", e);
			}
		} else {
			/* in case script has not been reflected, use the JSR-223 regular invoke */
			result = invokeScript(c, m, extendedInvoke, unwrapCircuitAbortException);
		}

		return result;
	}

	private static Method getGroovyMethod(Script script, String name) {
		Class<? extends Script> clazz = script.getClass();
		List<Method> methods = new ArrayList<Method>();

		for (Method m : clazz.getMethods()) {
			if (m.getName().equals(name)) {
				methods.add(m);
			}
		}

		switch (methods.size()) {
		case 0:
			Trace.info(String.format("No %s method defined on the script", name));
			break;
		case 1:
			return methods.get(0);
		default:
			throw new IllegalStateException(String.format("only one %s method is allowed", name));
		}

		return null;
	}

	private Object[] getGroovyInvokeArguments(Method method, Circuit circuit, Message msg) {
		Class<?>[] types = method.getParameterTypes();
		Object[] args = new Object[types.length];

		for (int index = 0; index < types.length; index++) {
			Class<?> type = types[index];

			if (type.isAssignableFrom(Message.class)) {
				args[index] = msg;
			} else if (type.isAssignableFrom(ExportedResources.class)) {
				args[index] = exports;
			} else if (type.isAssignableFrom(Circuit.class)) {
				args[index] = circuit;
			} else if (type.equals(AdvancedScriptProcessor.class)) {
				args[index] = this;
			} else {
				Trace.error("Unable to resolve arguments for invoke method");

				return null;
			}
		}

		return args;
	}

	private Object[] getGroovyDetachArguments(Method method) {
		Class<?>[] types = method.getParameterTypes();
		Object[] args = new Object[types.length];

		for (int index = 0; index < types.length; index++) {
			Class<?> type = types[index];

			if (type.equals(AdvancedScriptProcessor.class)) {
				args[index] = this;
			} else {
				Trace.error("Unable to resolve arguments for detach method");

				return null;
			}
		}

		return args;
	}

	private final class ExportedRuntime implements AdvancedScriptRuntime, GroovyScriptConfigurator {
		private void checkState() throws ScriptException {
			if (attached) {
				throw new ScriptException("This function can only be used during filter attachment");
			}
		}

		@Override
		public void setUnwrapCircuitAbortException(boolean unwrap) throws ScriptException {
			checkState();

			unwrapCircuitAbortException = unwrap;
		}

		@Override
		public void setExtendedInvoke(boolean extended) throws ScriptException {
			checkState();

			groovyInstance = null;
			groovyInvoke = null;
			groovyDetach = null;

			extendedInvoke = extended;
		}

		@Override
		public ContextResource getContextResource(String name) {
			return exports.getContextResource(name);
		}

		@Override
		public InvocableResource getInvocableResource(String name) {
			return exports.getInvocableResource(name);
		}

		@Override
		public KPSResource getKPSResource(String name) {
			return exports.getKPSResource(name);
		}

		@Override
		public CacheResource getCacheResource(String name) {
			ContextResource resource = getContextResource(name);

			return resource instanceof CacheResource ? (CacheResource) resource : null;
		}

		@Override
		public Boolean invokeResource(Message msg, String name) throws CircuitAbortException {
			return exports.invoke(msg, name);
		}

		@Override
		public Object substituteResource(Dictionary dict, String name) {
			return exports.substitute(dict, name);
		}

		@Override
		public void reflectResources(Script script) throws ScriptException {
			checkState();

			ExtensionContext.reflect(resources, script, getFilterName());
		}

		@Override
		public void reflectExtension(String name) throws ScriptException {
			checkState();

			ExtensionLoader.bind(resources, engine, this, name);
		}

		@Override
		public void reflectEntryPoints(Script script) throws ScriptException {
			checkState();

			Method detach = getGroovyMethod(script, "detach");
			Method invoke = getGroovyMethod(script, "invoke");

			if ((detach != null) && getGroovyDetachArguments(detach) == null) {
				throw new ScriptException("Unable to reflect detach arguments");
			}

			if ((invoke != null) && getGroovyInvokeArguments(invoke, null, null) == null) {
				throw new ScriptException("Unable to reflect invoke arguments");
			}

			if ((detach != null) || (invoke != null)) {
				groovyInstance = script;
				groovyDetach = detach;
				groovyInvoke = invoke;
			}
		}

		@Override
		public ContextResourceProvider getExportedResources() {
			return exports;
		}

		@Override
		public String getFilterName() {
			return getFilter().getName();
		}
	}

	private final class ExportedResources extends AbstractContextResourceProvider {
		@Override
		public ContextResource getContextResource(String name) {
			return resources.get(name);
		}
	}
}
