package com.vordel.circuit.groovy;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.MethodClosure;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.script.bind.ExtensionContext;
import com.vordel.circuit.script.context.resources.AbstractContextResourceProvider;
import com.vordel.circuit.script.context.resources.CacheResource;
import com.vordel.circuit.script.context.resources.ContextResource;
import com.vordel.circuit.script.context.resources.ContextResourceFactory;
import com.vordel.circuit.script.context.resources.ContextResourceProvider;
import com.vordel.circuit.script.context.resources.InvocableResource;
import com.vordel.circuit.script.context.resources.KPSResource;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.circuit.script.context.resources.SubstitutableResource;
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

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;

public class GroovyScriptProcessor extends MessageProcessor {
	// counter used to generate unique global Script class names
	private static int counter;

	static {
		counter = 0;
	}

	/**
	 * Attach script global function
	 */
	private static final String ATTACH_FUNCTION_NAME = "attach";
	/**
	 * Detach script global function
	 */
	private static final String DETACH_FUNCTION_NAME = "detach";
	/**
	 * Invoke script global function
	 */
	private static final String INVOKE_FUNCTION_NAME = "invoke";

	protected static final String SCRIPT_INVOKE = "INVOKE";
	protected static final String SCRIPT_JAXRS = "JAXRS";

	private Map<String, ContextResource> resources = null;
	private GroovyResourceExport exports = null;
	private GroovyClassLoader loader = null;

	private ScriptWebComponent jaxrsService = null;

	private Script script = null;
	private Method invoke = null;
	private Method detach = null;

	private Selector<Boolean> jaxrsReportNoMatch = null;

	@Override
	public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
		super.filterAttached(ctx, entity);

		EntityStore es = ctx.getStore();
		EntityType resourcesType = es.getTypeForName("ScriptResource");
		Collection<ESPK> resourceList = es.listChildren(entity.getPK(), resourcesType);
		ContextResourceFactory factory = ContextResourceFactory.getInstance();

		try {
			/* create resources and groovy class loader */
			factory.createResources(ctx, getFilter(), resources = new HashMap<String, ContextResource>(), resourceList);
			loader = new GroovyClassLoader(getParentLoader(), new CompilerConfiguration());

			/* parse, compile and create script instance (static initializers do not have access to resources) */
			script  = InvokerHelper.createScript(getScriptClass(entity), new Binding());

			/* retrieve script kind (direct invocation, shared method only, or web service) */
			String scriptKind = entity.getStringValue("scriptKind");

			/* create resource closures for vordel runtime */
			GroovyVordelRuntime runtime = new GroovyVordelRuntime(exports  = new GroovyResourceExport(Collections.unmodifiableMap(resources), ExtensionContext.bind(script)));
			ScriptWebComponent.Builder builder = null;
			
			for(Method method : runtime.getClass().getDeclaredMethods()) {
				String name = method.getName();

				script.setProperty(name, new MethodClosure(runtime, name));
			}

			/* now that we have the script instance , we can safely run the script */
			script.run();

			/* retrieve script detach method */
			detach  = getScriptMethod(DETACH_FUNCTION_NAME);

			if (SCRIPT_INVOKE.equals(scriptKind)) {
				/* regular script, search for invoke method */
				invoke = getScriptMethod(INVOKE_FUNCTION_NAME);

				if (invoke == null) {
					throw new EntityStoreException("script has no invoke() method");
				}
			} else if (SCRIPT_JAXRS.equals(scriptKind)) {
				/* jaxrs service, create a service builder and retrieve configuration */
				builder = ScriptWebComponent.builder();
				jaxrsReportNoMatch = SelectorResource.fromLiteral(entity.getStringValue("jaxrsReportNoMatch"), Boolean.class, true);
			}

			Method attach = getScriptMethod(ATTACH_FUNCTION_NAME);

			if (attach != null) {
				Class<?>[] types = attach.getParameterTypes();
				Object[] args = new Object[types.length];

				for(int index = 0; index < types.length; index++) {
					Class<?> type = types[index];

					if (type.isAssignableFrom(ConfigContext.class)) {
						args[index] = ctx;
					} else if (type.isAssignableFrom(Entity.class)) {
						args[index] = entity;
					} else if (type.isAssignableFrom(GroovyClassLoader.class)) {
						args[index] = loader;
					} else if (type.isAssignableFrom(ContextResourceProvider.class)) {
						args[index] = exports;
					} else if (type.isAssignableFrom(ScriptWebComponent.Builder.class)) {
						if (builder == null) {
							throw new EntityStoreException("web component builder can't be provided to attach method");
						}

						args[index] = builder;
					} else if (type.isAssignableFrom(MessageProcessor.class)) {
						args[index] = this;
					} else {
						throw new EntityStoreException("Unable to resolve arguments for attach method");
					}
				}

				try {
					/* invoke the attach method */
					attach.invoke(script, args);
				} catch (InvocationTargetException e) {
					Throwable cause = e.getCause();

					if (cause instanceof Error) {
						throw (Error) cause;
					} else if (cause instanceof EntityStoreException) {
						throw (EntityStoreException) cause;
					} else {
						throw new EntityStoreException("Unexpected exception during script attachment", e);
					}
				} catch (IllegalAccessException e) {
					throw new EntityStoreException("Unable to run script attach function", e);
				}
			}

			if (builder != null) {
				jaxrsService = builder.build(script);
			}
		} catch (EntityStoreException e) {
			filterDetached();

			throw e;
		} catch (RuntimeException e) {
			filterDetached();

			throw new EntityStoreException("Unable to initialize groovy script", e);
		}
	}

	@Override
	public void filterDetached() {
		super.filterDetached();

		if (detach != null) {
			Method method = detach;
			Script instance = script;

			script = null;
			detach = null;

			Class<?>[] types = method.getParameterTypes();
			Object[] args = new Object[types.length];

			for(int index = 0; index < types.length; index++) {
				Class<?> type = types[index];

				if (type.isAssignableFrom(GroovyClassLoader.class)) {
					args[index] = loader;
				} else if (type.isAssignableFrom(ContextResourceProvider.class)) {
					args[index] = exports;
				} else if (type.isAssignableFrom(MessageProcessor.class)) {
					args[index] = this;
				} else {
					args[index] = null;

					Trace.error("Unable to resolve arguments for detach method");
				}
			}

			try {
				/* invoke the detach method */
				method.invoke(instance, args);
			} catch (InvocationTargetException e) {
				Throwable cause = e.getCause();

				Trace.error("Unexpected exception during script detachment", cause);
			} catch (Exception e) {
				Trace.error("Unable to run script detach function", e);
			}
		}

		if (loader != null) {
			try {
				GroovyClassLoader instance = loader;

				loader = null;

				instance.close();
			} catch (IOException e) {
				Trace.error("Unexpected Exception when detaching filter", e);
			}
		}

		try {
			ContextResourceFactory.releaseResources(resources);
		} catch (Exception e) {
			Trace.error("Unexpected Exception when detaching filter", e);
		} finally {
			this.resources = null;
		}
	}

	@Override
	public boolean invoke(Circuit circuit, Message msg) throws CircuitAbortException {
		boolean result = false;

		GroovyScriptFilter filter = (GroovyScriptFilter) getFilter();
		Selector<String> attributeSelector = filter.getExportSelector();
		String attributeName = attributeSelector.substitute(msg);

		if ((attributeName != null) && (!attributeName.isEmpty())) {
			/* set script resources and exports in message */
			msg.put(attributeName, exports);
		}

		if (invoke != null) {
			/* invoke script */
			Class<?>[] types = invoke.getParameterTypes();
			Object[] args = new Object[types.length];

			for(int index = 0; index < types.length; index++) {
				Class<?> type = types[index];

				if (type.isAssignableFrom(Circuit.class)) {
					args[index] = circuit;
				} else if (type.isAssignableFrom(Message.class)) {
					args[index] = msg;
				} else if (type.isAssignableFrom(GroovyClassLoader.class)) {
					args[index] = loader;
				} else if (type.isAssignableFrom(MessageProcessor.class)) {
					args[index] = this;
				} else {
					throw new EntityStoreException("Unable to resolve arguments for invoke method");
				}
			}

			try {
				/* invoke the invoke method */
				Object value = invoke.invoke(script, args);

				if (value instanceof Boolean) {
					result = ((Boolean) value).booleanValue();

					if (Trace.isDebugEnabled()) {
						Trace.debug(String.format("method '%s' returned '%s'", invoke.getName(), value.toString()));
					}
				} else {
					throw new CircuitAbortException("script did not return a boolean");
				}
			} catch (InvocationTargetException e) {
				Throwable cause = e.getCause();

				if (cause instanceof Error) {
					throw (Error) cause;
				} else if (cause instanceof CircuitAbortException) {
					throw (CircuitAbortException) cause;
				} else {
					throw new CircuitAbortException("Unexpected exception during script invocation", cause);
				}
			} catch (Exception e) {
				throw new CircuitAbortException("Unable to run script invoke function", e);
			}
		} else if (jaxrsService != null) {
			/* JAXRS script... select behavior */
			Boolean reportNoMatch = jaxrsReportNoMatch == null ? null : jaxrsReportNoMatch.substitute(msg);

			if (reportNoMatch == null) {
				reportNoMatch = Boolean.FALSE;
			}

			if (reportNoMatch) {
				result = jaxrsService.filter(msg);
			} else {
				result = jaxrsService.service(msg);
			}
		} else {
			result = true;
		}

		return result;
	}

	// determine appropriate class loader to serve as parent loader
	// for GroovyClassLoader instance
	private static ClassLoader getParentLoader() {
		// check whether thread context loader can "see" Groovy Script class
		ClassLoader ctxtLoader = Thread.currentThread().getContextClassLoader();
		try {
			Class<?> c = ctxtLoader.loadClass(Script.class.getName());
	
			if (c == Script.class) {
				return ctxtLoader;
			}
		} catch (ClassNotFoundException cnfe) {
			/* ignore */
		}
		// exception was thrown or we get wrong class
		return Script.class.getClassLoader();
	}

	private Method getScriptMethod(String name) {
		Class<? extends Script> clazz = script.getClass();
		List<Method> methods = new ArrayList<Method>();
	
		for(Method m : clazz.getMethods()) {
			if (m.getName().equals(name)) {
				methods.add(m);
			}
		}
	
		switch(methods.size()) {
		case 0:
			Trace.info(String.format("No %s method defined on the script", name));
			break;
		case 1:
			return methods.get(0);
		default:
			throw new EntityStoreException(String.format("only one %s method is allowed", name));
		}
	
		return null;
	}

	private Class<?> getScriptClass(Entity entity) {
		try {
			String script = entity.getStringValue("script");
			Class<?> clazz = null;

			if (script != null) {
				String scriptName = entity.getStringValue("debugName");

				if ((scriptName == null) || scriptName.isEmpty()) {
					scriptName = String.format("Script%d.groovy", ++counter);
				}

				clazz = loader.parseClass(script, scriptName);
			}

			return clazz;
		} catch (CompilationFailedException e) {
			throw new EntityStoreException("Unable to compile script", e);
		}
	}

	public static class GroovyResourceExport extends AbstractContextResourceProvider {
		private final Map<String, ContextResource> resources;

		private final ExtensionContext exports;

		protected GroovyResourceExport(Map<String, ContextResource> resources, ExtensionContext exports) {
			this.resources = resources;
			this.exports = exports;
		}

		@Override
		public ContextResource getContextResource(String name) {
			ContextResource resource = exports.getContextResource(name);

			if (resource == null) {
				resource = resources.get(name);
			}

			return resource;
		}

	}

	public static class GroovyVordelRuntime {
		private final GroovyResourceExport exports;

		protected GroovyVordelRuntime(GroovyResourceExport exports) {
			this.exports = exports;
		}

		public final ContextResource getContextResource(Dictionary dict, String name) {
			return exports.getContextResource(name);
		}

		public final InvocableResource getInvocableResource(Dictionary dict, String name) {
			return exports.getInvocableResource(name);
		}

		public final SubstitutableResource<?> getSubstitutableResource(Dictionary dict, String name) {
			return exports.getSubstitutableResource(name);
		}

		public final KPSResource getKPSResource(Dictionary dict, String name) {
			return exports.getKPSResource(name);
		}

		public final CacheResource getCacheResource(Dictionary dict, String name) {
			ContextResource resource = getContextResource(dict, name);

			return resource instanceof CacheResource ? (CacheResource) resource : null;
		}

		public final boolean invokeResource(Circuit c, Message m, String name) throws CircuitAbortException {
			return exports.invoke(m, name);
		}

		public final Object substituteResource(Dictionary dict, String name) {
			return exports.substitute(dict, name);
		}
	}
}
