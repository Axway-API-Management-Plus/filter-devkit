package com.vordel.circuit.filter.devkit.context;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import com.vordel.circuit.filter.devkit.context.annotations.ExtensionModulePlugin;
import com.vordel.circuit.filter.devkit.context.resources.ContextResource;
import com.vordel.circuit.filter.devkit.script.advanced.AdvancedScriptRuntime;
import com.vordel.circuit.filter.devkit.script.advanced.AdvancedScriptRuntimeBinder;
import com.vordel.circuit.filter.devkit.script.extension.AbstractScriptExtension;
import com.vordel.circuit.filter.devkit.script.extension.AbstractScriptExtensionBuilder;
import com.vordel.circuit.filter.devkit.script.extension.ScriptExtension;
import com.vordel.common.Dictionary;
import com.vordel.config.ConfigContext;
import com.vordel.config.LoadableModule;
import com.vordel.el.Selector;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;
import com.vordel.trace.Trace;

public final class ExtensionLoader implements LoadableModule {
	private static final Map<String, ExtensionContext> LOADED_PLUGINS = new HashMap<String, ExtensionContext>();
	private static final Map<String, ScriptExtensionFactory> LOADED_SCRIPT_EXTENSIONS = new HashMap<String, ScriptExtensionFactory>();
	private static final Map<Class<?>, Object> LOADED_INTERFACES = new HashMap<Class<?>, Object>();

	private static final List<ExtensionModule> LOADED_MODULES = new LinkedList<ExtensionModule>();
	private static final List<Runnable> UNLOAD_CALLBACKS = new LinkedList<Runnable>();
	private static final Object SYNC = new Object();

	private static int load = 0;

	static {
		/* register extensions global name */
		Selector.addGlobalNamespace("extensions", getExtensionsDictionary());
	}

	@Override
	public void configure(ConfigContext ctx, Entity entity) throws EntityStoreException, FatalException {
		boolean configure = false;

		synchronized (SYNC) {
			configure = load == 0;

			if (load < Integer.MAX_VALUE) {
				/* just in case, handle integer overflow */
				load += 1;
			}
		}

		if (configure) {
			Trace.info("scanning services for Extensions");

			/* scan class path for invocable and subtituable methods */
			ExtensionScanner.scanClasses(ctx, Thread.currentThread().getContextClassLoader());

			Trace.info("services scanned");
		}
	}

	@Override
	public void load(LoadableModule l, String type) throws FatalException {
		/* nothing more to load */
	}

	@Override
	public void unload() {
		synchronized (SYNC) {
			if (load > 0) {
				/* just in case, handle integer overflow */
				load -= 1;
			}

			if (load == 0) {
				Iterator<ExtensionModule> modules = new ArrayList<ExtensionModule>(LOADED_MODULES).iterator();
				Iterator<Runnable> callbacks = new ArrayList<Runnable>(UNLOAD_CALLBACKS).iterator();

				while (callbacks.hasNext()) {
					try {
						callbacks.next().run();
					} catch (Exception e) {
						Trace.error("got error with unload callback", e);
					}

					callbacks.remove();
				}

				while (modules.hasNext()) {
					try {
						ExtensionModule module = modules.next();

						module.detachModule();

						Trace.info(String.format("unloaded '%s'", module.getClass().getName()));
					} catch (Exception e) {
						Trace.error("got error calling detach", e);
					}

					modules.remove();
				}

				LOADED_MODULES.clear();
				UNLOAD_CALLBACKS.clear();
				LOADED_PLUGINS.clear();
				LOADED_INTERFACES.clear();
				LOADED_SCRIPT_EXTENSIONS.clear();
			}
		}
	}

	public static void registerUndeployCallback(Runnable callback) {
		if (callback != null) {
			synchronized (SYNC) {
				Iterator<Runnable> iterator = UNLOAD_CALLBACKS.iterator();
				boolean contained = false;

				while ((!contained) && iterator.hasNext()) {
					contained = iterator.next() == callback;
				}

				if (!contained) {
					UNLOAD_CALLBACKS.add(0, callback);
				}
			}
		}
	}

	static void registerExtensionContext(String name, ExtensionContext resources) {
		if ((name != null) && (resources != null)) {
			synchronized (SYNC) {
				LOADED_PLUGINS.put(name, resources);
			}
		}
	}

	static void registerExtensionInstance(ConfigContext ctx, Object module) {
		if (module != null) {
			Class<?> mclazz = module.getClass();
			ExtensionModulePlugin plugin = mclazz.getAnnotation(ExtensionModulePlugin.class);
			ScriptExtension script = mclazz.getAnnotation(ScriptExtension.class);

			synchronized (SYNC) {
				if (module instanceof ExtensionModule) {
					registerExtensionModule(ctx, (ExtensionModule) module);
				}

				if (plugin != null) {
					for (Class<?> iclazz : plugin.value()) {
						if (iclazz.isInterface()) {
							Object pred = LOADED_INTERFACES.get(iclazz);

							if (pred != null) {
								Trace.error(String.format("Duplicate instance for interface '%s'", iclazz.getName()));
							} else if (iclazz.isAssignableFrom(mclazz)) {
								Trace.debug(String.format("registering interface instance for '%s'", iclazz.getName()));

								LOADED_INTERFACES.put(iclazz, module);
							} else {
								Trace.error(String.format("'%s' is not a valid interface for '%s'", iclazz.getName(), mclazz.getName()));
							}
						}
					}
				}

				if (script != null) {
					for (Class<?> iclazz : script.value()) {
						String name = iclazz.getName();

						if (iclazz.isInterface()) {
							Object pred = LOADED_SCRIPT_EXTENSIONS.get(name);

							if (pred != null) {
								Trace.error(String.format("Duplicate instance for script extension '%s'", name));
							} else if (iclazz.isAssignableFrom(mclazz)) {
								Trace.debug(String.format("registering script extension '%s'", name));

								LOADED_SCRIPT_EXTENSIONS.put(name, new ScriptExtensionFactory() {
									@Override
									public Object createExtensionInstance(AbstractScriptExtensionBuilder builder) throws ScriptException {
										return module;
									}

									@Override
									public Class<?> getExtensionInterface() {
										return iclazz;
									}
								});
							} else {
								Trace.error(String.format("'%s' is not a valid interface for '%s'", iclazz.getName(), mclazz.getName()));
							}
						}
					}
				}
			}
		}
	}

	public static <T extends AbstractScriptExtension> void registerScriptExtension(Constructor<T> constructor) {
		if (constructor != null) {
			synchronized (SYNC) {
				Class<T> mclazz = constructor.getDeclaringClass();
				ScriptExtension script = mclazz.getAnnotation(ScriptExtension.class);

				if (script != null) {
					for (Class<?> iclazz : script.value()) {
						String name = iclazz.getName();

						if (iclazz.isInterface()) {
							Object pred = LOADED_SCRIPT_EXTENSIONS.get(name);

							if (pred != null) {
								Trace.error(String.format("Duplicate instance for script extension '%s'", name));
							} else if (iclazz.isAssignableFrom(mclazz)) {
								Trace.debug(String.format("registering script extension '%s'", name));

								LOADED_SCRIPT_EXTENSIONS.put(name, new ScriptExtensionFactory() {
									@Override
									public Object createExtensionInstance(AbstractScriptExtensionBuilder builder) throws ScriptException {
										AbstractScriptExtension instance = null;

										try {
											constructor.setAccessible(true);
											instance = constructor.newInstance(builder);
										} catch (InvocationTargetException e) {
											Throwable cause = e.getCause();

											throw (ScriptException) new ScriptException(String.format("unable to instanciate script extension '%s'", name)).initCause(cause);
										} catch (Exception e) {
											throw (ScriptException) new ScriptException(String.format("unable to instanciate script extension '%s'", name)).initCause(e);
										} finally {
											constructor.setAccessible(false);
										}

										return instance;
									}

									@Override
									public Class<?> getExtensionInterface() {
										return iclazz;
									}
								});
							} else {
								Trace.error(String.format("'%s' is not a valid interface for '%s'", iclazz.getName(), mclazz.getName()));
							}
						}
					}
				}
			}
		}
	}

	private static void registerExtensionModule(ConfigContext ctx, ExtensionModule module) {
		Iterator<ExtensionModule> iterator = LOADED_MODULES.iterator();
		boolean contained = false;

		while ((!contained) && iterator.hasNext()) {
			contained = iterator.next() == module;
		}

		if (!contained) {
			Trace.info(String.format("initializing class '%s'", module.getClass().getName()));

			module.attachModule(ctx);

			LOADED_MODULES.add(0, module);
		}
	}

	public static void bind(Map<String, ContextResource> resources, ScriptEngine engine, AdvancedScriptRuntime runtime, String name) throws ScriptException {
		synchronized (SYNC) {
			ScriptExtensionFactory factory = LOADED_SCRIPT_EXTENSIONS.get(name);

			if (factory == null) {
				throw new ScriptException(String.format("script extension '%s' is not registered", name));
			}

			AbstractScriptExtensionBuilder builder = new AbstractScriptExtensionBuilder(runtime);
			AdvancedScriptRuntimeBinder binder = AdvancedScriptRuntimeBinder.getScriptBinder(engine);
			Object instance = factory.createExtensionInstance(builder);
			List<Method> methods = new ArrayList<Method>();

			/* reflect invocables/substitutables and extension functions */
			ExtensionContext.reflect(resources, instance);

			/* gather interface methods */
			scanInterfaces(factory.getExtensionInterface(), methods);

			/* bind proxy interface to script */
			binder.bind(engine, factory.proxify(instance), methods.toArray(new Method[0]));
		}
	}

	private static void scanInterfaces(Class<?> clazz, List<Method> methods) {
		Class<?> superClazz = clazz.getSuperclass();
		Class<?>[] interfaces = clazz.getInterfaces();

		for (Method method : clazz.getDeclaredMethods()) {
			methods.add(method);
		}

		if ((superClazz != null) && (superClazz.isInterface())) {
			scanInterfaces(superClazz, methods);
		}

		for (Class<?> impl : interfaces) {
			scanInterfaces(impl, methods);
		}
	}

	public static ExtensionContext getExtensionContext(String name) {
		synchronized (SYNC) {
			return LOADED_PLUGINS.get(name);
		}
	}

	public static <T> T getExtensionInstance(Class<T> clazz) {
		synchronized (SYNC) {
			Object instance = LOADED_INTERFACES.get(clazz);

			return instance == null ? null : clazz.cast(instance);
		}
	}

	private static Dictionary getExtensionsDictionary() {
		return new Dictionary() {
			@Override
			public Object get(String name) {
				return getExtensionContext(name);
			}
		};
	}
}
