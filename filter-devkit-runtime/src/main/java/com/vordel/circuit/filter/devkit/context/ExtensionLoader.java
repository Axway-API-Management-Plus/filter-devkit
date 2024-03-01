package com.vordel.circuit.filter.devkit.context;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import com.vordel.circuit.filter.devkit.context.annotations.ExtensionModulePlugin;
import com.vordel.circuit.filter.devkit.script.extension.ScriptExtension;
import com.vordel.circuit.filter.devkit.script.extension.ScriptExtensionBinder;
import com.vordel.common.Dictionary;
import com.vordel.config.ConfigContext;
import com.vordel.config.LoadableModule;
import com.vordel.el.Selector;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;
import com.vordel.trace.Trace;

public final class ExtensionLoader implements LoadableModule {
	private static final Map<String, ExtensionContext> LOADED_PLUGINS = new HashMap<String, ExtensionContext>();
	private static final Map<Class<?>, ScriptExtension> LOADED_SCRIPT_EXTENSIONS = new HashMap<Class<?>, ScriptExtension>();
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
			ExtensionModulePlugin annotation = module.getClass().getAnnotation(ExtensionModulePlugin.class);

			synchronized (SYNC) {
				if (module instanceof ExtensionModule) {
					registerExtensionModule(ctx, (ExtensionModule) module);
				}

				if (module instanceof ScriptExtension) {
					registerScriptExtension(ctx, (ScriptExtension) module);
				}

				if (annotation != null) {
					for (Class<?> clazz : annotation.value()) {
						if (clazz.isInterface()) {
							Object pred = LOADED_INTERFACES.get(clazz);

							if (pred != null) {
								Trace.error(String.format("Duplicate instance for interface '%s'", clazz.getName()));
							} else {
								Trace.debug(String.format("registering interface instance for '%s'", clazz.getName()));

								LOADED_INTERFACES.put(clazz, module);
							}
						}
					}
				}
			}
		}
	}

	private static void registerScriptExtension(ConfigContext ctx, ScriptExtension module) {
		Set<Class<?>> extensions = new HashSet<Class<?>>();
		Class<?> moduleClazz = module.getClass();
		
		/* search for script extensions implementations */
		scanScriptExtension(moduleClazz, extensions);

		/* hide extension behind a proxy to display debug messages */
		ClassLoader loader = moduleClazz.getClassLoader();
		InvocationHandler handler = new ScriptExtensionHandler(module);
		ScriptExtension proxy = ScriptExtension.class.cast(Proxy.newProxyInstance(loader, extensions.toArray(new Class<?>[0]), handler));
		
		for (Class<?> extensionClazz : extensions) {
			Object pred = LOADED_SCRIPT_EXTENSIONS.get(extensionClazz);

			if (pred != null) {
				Trace.error(String.format("Duplicate instance for script extension '%s'", extensionClazz.getName()));
			} else {
				Trace.debug(String.format("registering script extension '%s'", extensionClazz.getName()));

				LOADED_SCRIPT_EXTENSIONS.put(extensionClazz, proxy);
			}
		}
	}

	private static void scanScriptExtension(Class<?> clazz, Set<Class<?>> extensions) {
		if (!ScriptExtension.class.equals(clazz)) {
			Class<?> superClazz = clazz.getSuperclass();
			Class<?>[] interfaces = clazz.getInterfaces();

			if (clazz.isInterface() && ScriptExtension.class.isAssignableFrom(clazz)) {
				extensions.add(clazz);
			}

			if (superClazz != null) {
				scanScriptExtension(superClazz, extensions);
			}

			for (Class<?> impl : interfaces) {
				scanScriptExtension(impl, extensions);
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

	public static void bindScriptExtensions(ScriptEngine engine, ScriptExtensionBinder binder) throws ScriptException {
		synchronized (SYNC) {
			for (Entry<Class<?>, ScriptExtension> entry : LOADED_SCRIPT_EXTENSIONS.entrySet()) {
				Class<?> clazz = entry.getKey();
				ScriptExtension runtime = entry.getValue();

				Trace.debug(String.format("binding script extension '%s'", clazz.getName()));

				try {
					binder.bindRuntime(engine, runtime, clazz);
				} catch (Exception e) {
					Trace.error(String.format("unable to bind script extension '%s'", clazz.getName()), e);
				}
			}
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
