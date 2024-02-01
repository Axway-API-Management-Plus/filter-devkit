package com.vordel.circuit.script.advanced;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.script.AbstractScriptProcessor;
import com.vordel.circuit.script.bind.ExtensionContext;
import com.vordel.circuit.script.context.resources.AbstractContextResourceProvider;
import com.vordel.circuit.script.context.resources.CacheResource;
import com.vordel.circuit.script.context.resources.ContextResource;
import com.vordel.circuit.script.context.resources.ContextResourceFactory;
import com.vordel.circuit.script.context.resources.ContextResourceProvider;
import com.vordel.circuit.script.context.resources.InvocableResource;
import com.vordel.circuit.script.context.resources.KPSResource;
import com.vordel.circuit.script.jaxrs.ScriptWebComponent;
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

public abstract class AdvancedScriptProcessor extends AbstractScriptProcessor {
	/**
	 * Resources retrieved from configured UI
	 */
	private Map<String, ContextResource> resources = null;

	/**
	 * Script execution context
	 */
	private ScriptEngine engine = null;

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
	 * if not null, the service will replace the invoke() script call (Groovy
	 * scripts only)
	 */
	private ScriptWebComponent jaxrsService = null;
	/**
	 * if the JAXRS service does not match any of its configured resources, return
	 * false instead of creating a 404 response (with the filter returning success)
	 */
	private boolean jaxrsReportNoMatch = false;

	/**
	 * Resources reflected from groovy scripts
	 */
	private ExtensionContext exports = null;

	/**
	 * Instance of reflected groovy script
	 */
	private Object groovyInstance = null;
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
	private final ExportedResources context = new ExportedResources();

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
	public boolean invoke(Circuit c, Message m) throws CircuitAbortException {
		boolean result = false;

		if (jaxrsService != null) {
			if (jaxrsReportNoMatch) {
				result = jaxrsService.filter(m);
			} else {
				result = jaxrsService.service(m);
			}

			return result;
		} else {
			result = super.invoke(c, m);
		}

		return result;
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
			detachScript();
		}

		try {
			ContextResourceFactory.releaseResources(resources);
		} catch (Exception e) {
			Trace.error("Unexpected Exception when detaching filter", e);
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
		factory.createResources(ctx, getFilter(), resources = new HashMap<String, ContextResource>(), resourceList);
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
		/*
		 * singe engine may be registered under multiple names, retrieve language name
		 * from factory
		 */
		ScriptEngineFactory factory = engine.getFactory();
		String language = factory.getLanguageName();

		/* create the runtime object used to create function closures */
		ExportedRuntime runtime = new ExportedRuntime();

		if ("Groovy".equals(language)) {
			AdvancedScriptRuntimeBinder.getGroovyBinder().bindRuntime(engine, runtime);
		} else if ("ECMAScript".equals(language) || "EmbeddedECMAScript".equals(language)) {
			AdvancedScriptRuntimeBinder.getJavascriptBinder().bindRuntime(engine, runtime);
		} else if ("python".equals(language)) {
			AdvancedScriptRuntimeBinder.getPythonBinder().bindRuntime(engine, runtime);
		}

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
				Throwable cause = e.getCause();

				if (cause instanceof Error) {
					throw (Error) cause;
				} else if (cause instanceof RuntimeException) {
					throw (RuntimeException) cause;
				} else if (cause instanceof CircuitAbortException) {
					throw (CircuitAbortException) cause;
				} else {
					throw new CircuitAbortException("Unexpected exception during groovy invocation", cause);
				}
			} catch (Exception e) {
				throw new CircuitAbortException("Unable to run groovy invoke function", e);
			}
		} else {
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
			} else if (type.isAssignableFrom(ContextResourceProvider.class)) {
				args[index] = context;
			} else if (type.isAssignableFrom(Circuit.class)) {
				args[index] = circuit;
			} else if (type.isAssignableFrom(MessageProcessor.class)) {
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

			if (type.isAssignableFrom(ContextResourceProvider.class)) {
				args[index] = context;
			} else if (type.isAssignableFrom(MessageProcessor.class)) {
				args[index] = this;
			} else {
				Trace.error("Unable to resolve arguments for detach method");

				return null;
			}
		}

		return args;
	}

	private class ExportedRuntime implements AdvancedScriptRuntime {
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

			jaxrsService = null;
			groovyInstance = null;
			groovyInvoke = null;
			groovyDetach = null;

			extendedInvoke = extended;
		}

		@Override
		public ContextResource getContextResource(String name) {
			return context.getContextResource(name);
		}

		@Override
		public InvocableResource getInvocableResource(String name) {
			return context.getInvocableResource(name);
		}

		@Override
		public KPSResource getKPSResource(String name) {
			return context.getKPSResource(name);
		}

		@Override
		public CacheResource getCacheResource(String name) {
			return context.getCacheResource(name);
		}

		@Override
		public Boolean invokeResource(Message msg, String name) throws CircuitAbortException {
			return context.invoke(msg, name);
		}

		@Override
		public Object substituteResource(Dictionary dict, String name) {
			return context.substitute(dict, name);
		}

		@Override
		public void reflectResources(Script script) throws ScriptException {
			checkState();

			if (exports != null) {
				throw new ScriptException("This function can only be called once");
			}

			exports = ExtensionContext.bind(script);
		}

		@Override
		public void reflectEntryPoints(Script script) throws ScriptException {
			checkState();

			jaxrsService = null;

			Method detach = getGroovyMethod(script, "detach");
			Method invoke = getGroovyMethod(script, "invoke");

			if (getGroovyDetachArguments(detach) == null) {
				detach = null;
			}

			if (getGroovyInvokeArguments(invoke, null, null) == null) {
				invoke = null;
			}

			if ((detach != null) || (invoke != null)) {
				groovyInstance = script;
				groovyDetach = detach;
				groovyInvoke = invoke;
			}
		}

		@Override
		public void setScriptWebComponent(ScriptWebComponent jaxrs, boolean reportNoMatch) throws ScriptException {
			checkState();

			jaxrsService = jaxrs;
			jaxrsReportNoMatch = reportNoMatch;
		}

		@Override
		public ContextResourceProvider getExportedResources() {
			return context;
		}
	}

	private class ExportedResources extends AbstractContextResourceProvider {
		@Override
		public ContextResource getContextResource(String name) {
			ContextResource resource = exports == null ? null : exports.getContextResource(name);

			if (resource == null) {
				resource = resources.get(name);
			}

			return resource;
		}

		public final CacheResource getCacheResource(String name) {
			ContextResource resource = getContextResource(name);

			return resource instanceof CacheResource ? (CacheResource) resource : null;
		}
	}
}
