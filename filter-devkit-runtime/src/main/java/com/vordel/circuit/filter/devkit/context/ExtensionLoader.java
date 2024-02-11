package com.vordel.circuit.filter.devkit.context;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.vordel.circuit.filter.devkit.context.annotations.ExtensionProviderClasses;
import com.vordel.circuit.filter.devkit.context.provider.ExtensionProvider;
import com.vordel.common.Dictionary;
import com.vordel.config.ConfigContext;
import com.vordel.config.LoadableModule;
import com.vordel.el.Selector;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;
import com.vordel.trace.Trace;

public final class ExtensionLoader implements LoadableModule {
	private static final Map<String, ExtensionContext> LOADED_PLUGINS = new HashMap<String, ExtensionContext>();
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
	}

	@Override
	public void unload() {
		synchronized (SYNC) {
			if (load > 0) {
				/* just in case, handle integer overflow */
				load -= 1;
			}

			if (load == 0) {
				Iterator<ExtensionModule> modules = LOADED_MODULES.iterator();
				Iterator<Runnable> callbacks = UNLOAD_CALLBACKS.iterator();

				LOADED_PLUGINS.clear();

				while (callbacks.hasNext()) {
					try {
						callbacks.next().run();
						callbacks.remove();
					} catch (Exception e) {
						Trace.error("got error with unload callback", e);
					}
				}

				while (modules.hasNext()) {
					try {
						ExtensionModule module = modules.next();

						modules.remove();
						module.detachModule();

						Trace.info(String.format("unloaded '%s'", module.getClass().getName()));
					} catch (Exception e) {
						Trace.error("got error calling detach", e);
					}
				}
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

	public static void registerModule(ConfigContext ctx, ExtensionModule module) {
		if (module != null) {
			synchronized (SYNC) {
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
		}
	}

	public static void registerGlobalResources(String name, ExtensionContext resources) {
		if ((name != null) && (resources != null)) {
			synchronized (SYNC) {
				LOADED_PLUGINS.put(name, resources);
			}
		}
	}

	public static ExtensionContext getGlobalResources(String name) {
		synchronized (SYNC) {
			return LOADED_PLUGINS.get(name);
		}
	}

	private static Dictionary getExtensionsDictionary() {
		return new Dictionary() {
			@Override
			public Object get(String name) {
				return getGlobalResources(name);
			}
		};
	}
}
