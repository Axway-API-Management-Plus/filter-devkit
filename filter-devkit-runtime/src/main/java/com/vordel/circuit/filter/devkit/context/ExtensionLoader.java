package com.vordel.circuit.filter.devkit.context;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.script.ScriptException;

import com.vordel.circuit.filter.devkit.context.annotations.ExtensionInstance;
import com.vordel.circuit.filter.devkit.script.extension.AbstractScriptExtension;
import com.vordel.circuit.filter.devkit.script.extension.ScriptExtensionBuilder;
import com.vordel.circuit.filter.devkit.script.extension.ScriptExtensionFactory;
import com.vordel.circuit.filter.devkit.script.extension.annotations.ScriptExtension;
import com.vordel.common.Dictionary;
import com.vordel.config.ConfigContext;
import com.vordel.config.LoadableModule;
import com.vordel.el.Selector;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;
import com.vordel.trace.Trace;

/**
 * Filter DevKit extension loader. This is the only loadable module implemented.
 * This module takes care of class path scanning (of files preprocessed by the
 * annotation processor). It exposes static methods to retrieve registered
 * extensions.
 * 
 * @author rdesaintleger@axway.com
 */
public final class ExtensionLoader implements LoadableModule {
	private static final Map<String, ExtensionResourceProvider> LOADED_PLUGINS = new HashMap<String, ExtensionResourceProvider>();
	private static final Map<String, ScriptExtensionFactory> LOADED_SCRIPT_EXTENSIONS = new HashMap<String, ScriptExtensionFactory>();
	private static final Map<Class<?>, Object> LOADED_INTERFACES = new HashMap<Class<?>, Object>();

	private static final List<ExtensionModule> LOADED_MODULES = new LinkedList<ExtensionModule>();
	private static final List<Runnable> UNLOAD_CALLBACKS = new LinkedList<Runnable>();
	private static final Set<String> REGISTERED = new HashSet<String>();
	private static final Object SYNC = new Object();

	private static boolean loaded = false;

	/**
	 * extensions dictionary for selector only access.
	 */
	private static final Dictionary EXTENSIONS_NAMESPACE = new Dictionary() {
		@Override
		public Object get(String name) {
			return getExtensionContext(name);
		}
	};

	static {
		/* register extensions global name */
		Selector.addGlobalNamespace("extensions", EXTENSIONS_NAMESPACE);
	}

	@Override
	public void configure(ConfigContext ctx, Entity entity) throws EntityStoreException, FatalException {
		synchronized (SYNC) {
			reset();

			loaded = true;

			Trace.info("scanning services for Extensions");

			REGISTERED.clear();

			/* scan class path for extensions */
			scanClasses(ctx, Thread.currentThread().getContextClassLoader());
		}

		Trace.info("services scanned");
	}

	public static final boolean isLoaded() {
		synchronized (SYNC) {
			return loaded;
		}
	}

	@Override
	public void load(LoadableModule l, String type) throws FatalException {
		/* nothing to load */
	}

	private static final void checkLoadState() {
		synchronized (SYNC) {
			if (!loaded) {
				throw new IllegalStateException("ExtensionLoader module is not available");
			}
		}
	}

	@Override
	public void unload() {
		synchronized (SYNC) {
			reset();
		}
	}

	private void reset() {
		synchronized (SYNC) {
			if (loaded) {
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

				loaded = false;
			}

			LOADED_MODULES.clear();
			UNLOAD_CALLBACKS.clear();
			LOADED_PLUGINS.clear();
			LOADED_INTERFACES.clear();
			LOADED_SCRIPT_EXTENSIONS.clear();
			REGISTERED.clear();
		}
	}

	public static final void scanClasses(ConfigContext ctx, ClassLoader loader) {
		synchronized (SYNC) {
			/* scan class path for extensions */
			List<Class<?>> scanned = ExtensionScanner.scanExtensions(loader, REGISTERED, null);

			/* register scanned classes */
			ExtensionScanner.registerClasses(ctx, scanned);
		}
	}

	/**
	 * Allow any script extension to register undeploy callbacks.
	 * 
	 * @param callback action to be executed before shutdown.
	 */
	public static final void registerUndeployCallback(Runnable callback) {
		if (callback != null) {
			synchronized (SYNC) {
				checkLoadState();

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

	/**
	 * package private entry to register extension context from scanned classes
	 * 
	 * @param name      name of context (exposed to global namespace)
	 * @param resources context to be registered
	 */
	static final void registerExtensionContext(String name, ExtensionResourceProvider resources) {
		if ((name != null) && (resources != null)) {
			synchronized (SYNC) {
				checkLoadState();

				LOADED_PLUGINS.put(name, resources);
			}
		}
	}

	/**
	 * package private entry to register a class instance. instance can be any
	 * object which have a no-arg contructor and annotated with
	 * {@link ExtensionInstance} or {@link ScriptExtension}. Relevant interfaces or
	 * script extensions get registered at this point.
	 * 
	 * @param ctx    configuration argument that will be passed to the
	 *               {@link ExtensionModule#attachModule(ConfigContext)} if
	 *               applicable
	 * @param module instance of the object to be registered.
	 */
	static final void registerExtensionInstance(ConfigContext ctx, Object module) {
		if (module != null) {
			checkLoadState();

			Class<?> mclazz = module.getClass();
			ExtensionInstance plugin = mclazz.getAnnotation(ExtensionInstance.class);
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
								Trace.info(String.format("registering interface instance for '%s'", iclazz.getName()));

								LOADED_INTERFACES.put(iclazz, module);
							} else {
								Trace.error(String.format("'%s' is not a valid interface for '%s'", iclazz.getName(), mclazz.getName()));
							}
						} else {
							Trace.error(String.format("'%s' is not an interface", iclazz.getName()));
						}
					}
				}

				if (script != null) {
					registerScriptExtension(LOADED_SCRIPT_EXTENSIONS, mclazz, (iclazz) -> createScriptExtensionInstanceFactory(module, iclazz), true);
				}
			}
		}
	}

	/**
	 * package private entry to register a script extension which need to interact
	 * with the script resources.
	 * 
	 * @param <T>         represent the contructor declaring class.
	 * @param constructor reflected contructor for the extension.
	 */
	static final <T extends AbstractScriptExtension> void registerScriptExtension(Constructor<T> constructor) {
		if (constructor != null) {
			synchronized (SYNC) {
				checkLoadState();

				Class<T> mclazz = constructor.getDeclaringClass();

				registerScriptExtension(LOADED_SCRIPT_EXTENSIONS, mclazz, (iclazz) -> createScriptExtensionFactory(constructor, iclazz), true);
			}
		}
	}

	private static final void registerScriptExtension(Map<String, ScriptExtensionFactory> registry, Class<?> mclazz, Function<Class<?>[], ScriptExtensionFactory> builder, boolean global) {
		ScriptExtension script = mclazz.getAnnotation(ScriptExtension.class);

		if (script != null) {
			List<Class<?>> factories = new ArrayList<Class<?>>();

			for (Class<?> iclazz : script.value()) {
				String name = iclazz.getName();

				if (iclazz.isInterface()) {
					Object pred = registry.get(name);

					if (pred != null) {
						Trace.error(String.format("Duplicate instance for script extension '%s'", name));
					} else if (iclazz.isAssignableFrom(mclazz)) {
						ScriptExtensionFactory factory = builder.apply(new Class<?>[] { iclazz });

						if (global) {
							/* do not display register message is global registry is not available */
							Trace.info(String.format("registering script extension interface '%s'", name));
						}

						registry.put(name, factory);
						factories.add(iclazz);
					} else {
						Trace.error(String.format("'%s' is not a valid interface for '%s'", iclazz.getName(), mclazz.getName()));
					}
				} else {
					Trace.error(String.format("'%s' is not an interface", iclazz.getName()));
				}
			}

			if (global) {
				/* do not display register message is global registry is not available */
				Trace.info(String.format("registering script extension implementation '%s'", mclazz.getName()));
			}

			registry.put(mclazz.getName(), builder.apply(factories.toArray(new Class<?>[0])));
		}
	}

	private static final ScriptExtensionFactory createScriptExtensionInstanceFactory(Object instance, Class<?>... iclazz) {
		return new ScriptExtensionFactory() {
			@Override
			protected Object createExtensionInstance(ScriptExtensionBuilder builder) throws ScriptException {
				return instance;
			}

			@Override
			public void scanScriptExtension(List<Method> methods) {
				scanScriptExtensionInterfaces(methods, iclazz);
			}

			@Override
			public Object proxify(Object instance) {
				/* if iclazz has a single entry use its loader */
				Class<?> clazz = iclazz.length == 1 ? iclazz[0] : instance.getClass();
				ClassLoader loader = clazz.getClassLoader();

				return proxify(instance, loader, iclazz);
			}

			@Override
			public boolean isLoaded(Set<String> loaded) {
				/* if iclazz has a single entry use its name */
				Class<?> clazz = iclazz.length == 1 ? iclazz[0] : instance.getClass();

				return !loaded.add(clazz.getName());
			}
		};
	}

	private static final ScriptExtensionFactory createScriptExtensionFactory(Constructor<?> constructor, Class<?>... iclazz) {
		return new ScriptExtensionFactory() {
			@Override
			protected Object createExtensionInstance(ScriptExtensionBuilder builder) throws ScriptException {
				Object instance = null;
				Class<?> mclazz = constructor.getDeclaringClass();
				String name = mclazz.getName();

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
			public Object proxify(Object instance) {
				/* if iclazz has a single entry use its loader */
				Class<?> clazz = iclazz.length == 1 ? iclazz[0] : instance.getClass();
				ClassLoader loader = clazz.getClassLoader();

				return proxify(instance, loader, iclazz);
			}

			@Override
			public void scanScriptExtension(List<Method> methods) {
				scanScriptExtensionInterfaces(methods, iclazz);
			}

			@Override
			public boolean isLoaded(Set<String> loaded) {
				/* if iclazz has a single entry use its name */
				Class<?> clazz = iclazz.length == 1 ? iclazz[0] : constructor.getDeclaringClass();

				return !loaded.add(clazz.getName());
			}
		};
	}

	/**
	 * effective registration of an {@link ExtensionModule}. This will call the
	 * attach() method and save the initialized module to be able to call the
	 * detach() method.
	 * 
	 * @param ctx    configuration argument that will be passed to the
	 *               {@link ExtensionModule#attachModule(ConfigContext)}
	 * @param module module to be registered
	 */
	private static final void registerExtensionModule(ConfigContext ctx, ExtensionModule module) {
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

	public static final ScriptExtensionFactory getScriptExtensionFactory(String name) throws ScriptException {
		synchronized (SYNC) {
			ScriptExtensionFactory factory = null;

			if (isLoaded()) {
				factory = LOADED_SCRIPT_EXTENSIONS.get(name);
			} else {
				Set<String> allowed = Collections.singleton(name);
				ClassLoader loader = ScriptExtension.class.getClassLoader();

				/* scan extension for this specific class only */
				List<Class<?>> scanned = ExtensionScanner.scanExtensions(loader, new HashSet<String>(), allowed);
				Map<String, ScriptExtensionFactory> registry = new HashMap<String, ScriptExtensionFactory>();

				for (Class<?> mclazz : scanned) {
					if (ExtensionModule.class.isAssignableFrom(mclazz)) {
						throw new ScriptException(String.format("unable to initialize script extension '%s', ExtensionLoader is not active", name));
					}

					if (Modifier.isAbstract(mclazz.getModifiers())) {
						throw new ScriptException(String.format("unable to initialize script extension '%s', class is abstract", name));
					}

					ScriptExtension script = mclazz.getAnnotation(ScriptExtension.class);

					if (script == null) {
						throw new ScriptException(String.format("'%s' is not a script extension", name));
					}

					try {
						if (AbstractScriptExtension.class.isAssignableFrom(mclazz)) {
							/* fallback to the abstract script extension mechanism */
							Constructor<?> constructor = mclazz.getDeclaredConstructor(ScriptExtensionBuilder.class);

							registerScriptExtension(registry, mclazz, (iclazz) -> createScriptExtensionFactory(constructor, iclazz), false);
						} else {
							/* create a new instance and register it now */
							Object instance = ExtensionScanner.newInstance(mclazz);

							registerScriptExtension(registry, mclazz, (iclazz) -> createScriptExtensionInstanceFactory(instance, iclazz), false);
						}
					} catch (InvocationTargetException e) {
						Throwable cause = e.getCause();

						throw (ScriptException) new ScriptException(String.format("unable to instanciate script extension '%s'", name)).initCause(cause);
					} catch (NoSuchMethodException e) {
						throw (ScriptException) new ScriptException(String.format("no suitable constructo found for script extension '%s'", name)).initCause(e);
					} catch (Exception e) {
						throw (ScriptException) new ScriptException(String.format("unable to instanciate script extension '%s'", name)).initCause(e);
					}
				}

				/* finally, retrieve extension factory */
				factory = registry.get(name);
			}

			return factory;
		}
	}

	/**
	 * Retrieve a extension context using the registration name
	 * 
	 * @param name name of the extension
	 * @return the registered context or <code>null</code> if none.
	 */
	public static final ExtensionResourceProvider getExtensionContext(String name) {
		synchronized (SYNC) {
			checkLoadState();

			return LOADED_PLUGINS.get(name);
		}
	}

	/**
	 * returns a registered interface instance or <code>null</code> if none exists
	 * 
	 * @param <T>   interface type parameter
	 * @param clazz interface class definition
	 * @return registered instance or <code>null</code> if none
	 */
	public static final <T> T getExtensionInstance(Class<T> clazz) {
		synchronized (SYNC) {
			checkLoadState();

			Object instance = LOADED_INTERFACES.get(clazz);

			return instance == null ? null : clazz.cast(instance);
		}
	}
}
