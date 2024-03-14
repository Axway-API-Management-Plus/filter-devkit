package com.vordel.circuit.filter.devkit.context;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Priority;

import com.vordel.circuit.filter.devkit.context.annotations.ExtensionContext;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionInstance;
import com.vordel.circuit.filter.devkit.context.resources.SelectorResource;
import com.vordel.circuit.filter.devkit.script.extension.AbstractScriptExtension;
import com.vordel.circuit.filter.devkit.script.extension.AbstractScriptExtensionBuilder;
import com.vordel.circuit.filter.devkit.script.extension.ScriptExtension;
import com.vordel.common.Dictionary;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.trace.Trace;

public class ExtensionScanner {
	private ExtensionScanner() {
	}

	private static void fromClass(ConfigContext ctx, Class<?> clazz) {
		ExtensionContext annotation = clazz.getAnnotation(ExtensionContext.class);
		Object instance = null;

		if (isInstantiable(clazz)) {
			try {
				/* class is annotated is an extension module */
				Constructor<?> constructor = clazz.getDeclaredConstructor();

				try {
					constructor.setAccessible(true);
					instance = constructor.newInstance();

					/* attach module */
					ExtensionLoader.registerExtensionInstance(ctx, instance);
				} finally {
					constructor.setAccessible(false);
				}
			} catch (InstantiationException e) {
				Trace.error(String.format("the class '%s' can't be instantiated", clazz.getName()), e);
			} catch (IllegalAccessException e) {
				Trace.error(String.format("the no-arg constructor for class '%s' is not accessible", clazz.getName()), e);
			} catch (InvocationTargetException e) {
				Throwable cause = e.getCause();

				Trace.error(String.format("got error instantiating class '%s'", clazz.getName()), cause);
			} catch (NoSuchMethodException e) {
				Trace.error(String.format("the class '%s' must have a no-arg constructor", clazz.getName()), e);
			}
		} else if (isScriptExtension(clazz)) {
			try {
				/* class is annotated is an extension module */
				Constructor<? extends AbstractScriptExtension> constructor = clazz.asSubclass(AbstractScriptExtension.class).getDeclaredConstructor(AbstractScriptExtensionBuilder.class);

				ExtensionLoader.registerScriptExtension(constructor);
			} catch (NoSuchMethodException e) {
				Trace.error(String.format("the class '%s' must have a no-arg constructor", clazz.getName()), e);
			}
		}

		if (annotation != null) {
			register(instance, clazz);
		}
	}

	public static ExtensionResourceProvider fromClass(Class<?> clazz) {
		return register(null, clazz);
	}

	public static <T> ExtensionResourceProvider fromInstance(T object) {
		Class<?> clazz = object == null ? null : object.getClass();

		return register(object, clazz);
	}

	private static Comparator<Class<?>> PRIORITY_COMPARATOR = new Comparator<Class<?>>() {
		@Override
		public int compare(Class<?> o1, Class<?> o2) {
			Priority p1 = o1.getAnnotation(Priority.class);
			Priority p2 = o2.getAnnotation(Priority.class);
			int i1 = p1 == null ? 0 : p1.value();
			int i2 = p2 == null ? 0 : p2.value();

			return Integer.compare(i1, i2);
		}
	};

	private static List<Class<?>> scanExtensions(ClassLoader loader, Set<String> extensions) {
		Set<String> clazzes = new HashSet<String>();

		try {
			Enumeration<URL> configs = loader.getResources("META-INF/vordel/extensions");

			while (configs.hasMoreElements()) {
				URL config = configs.nextElement();
				InputStream in = config.openStream();

				try {
					BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

					try {
						String line = null;

						while ((line = reader.readLine()) != null) {
							line = line.trim();

							if (!line.isEmpty()) {
								clazzes.add(line);
							}
						}
					} finally {
						reader.close();
					}
				} finally {
					in.close();
				}
			}
		} catch (Exception e) {
			Trace.error("Unable to scan extensions", e);
		}

		List<Class<?>> scanned = new ArrayList<Class<?>>();

		for (String clazzName : clazzes) {
			if (extensions.add(clazzName)) {
				try {
					/*
					 * create classes which expose ExtensionContext or ExtensionInstance annotation
					 */
					scanned.add(Class.forName(clazzName, false, getClassLoader(loader, clazzName)));
				} catch (Exception e) {
					Trace.error(String.format("Got exception loading class '%s'", clazzName), e);
				} catch (Error e) {
					Trace.error(String.format("Got error loading class '%s'", clazzName), e);
				}
			}
		}

		for (Class<?> loaded : scanned) {
			String clazzName = loaded.getName();

			try {
				/* initialize previously created classes */
				Class.forName(clazzName, true, loaded.getClassLoader());
			} catch (Exception e) {
				Trace.error(String.format("Got exception initializing class '%s'", clazzName), e);
			}
		}

		return scanned;
	}

	/**
	 * create a child first classloader for an extension module (or context)
	 * 
	 * @param loader    parent ClassLoader
	 * @param clazzName class to be loaded.
	 * @return
	 */
	private static ClassLoader getClassLoader(ClassLoader loader, String clazzName) {
		String libs = String.format("META-INF/vordel/libraries/%s", clazzName);
		URL jars = loader.getResource(libs);
		Set<File> scanned = new HashSet<File>();

		if (jars != null) {
			try {
				InputStream in = jars.openStream();

				try {
					BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

					try {
						String line = null;

						while ((line = reader.readLine()) != null) {
							line = line.trim();

							if (!line.isEmpty()) {
								Selector<String> selector = SelectorResource.fromLiteral(line, String.class, true);
								File file = new File(selector.substitute(Dictionary.empty));

								if (!file.exists()) {
									Trace.error(String.format("path '%s' does not exists for module '%s'", selector.getLiteral(), clazzName));
								}

								scanJavaArchives(file, scanned);
							}
						}
					} finally {
						reader.close();
					}

				} finally {
					in.close();
				}
			} catch (IOException e) {
				Trace.error(String.format("Got error reading module libraries for '%s'", clazzName), e);
			}

			Set<URL> urls = new HashSet<URL>();

			for (File file : scanned) {
				try {
					urls.add(file.toURI().toURL());
				} catch (MalformedURLException e) {
					Trace.error(String.format("can't normalize file path '%s' to URL", file.getAbsolutePath()), e);
				}
			}

			loader = new ExtensionClassLoader(getForceLoads(loader, clazzName), urls.toArray(new URL[0]), loader);
		}

		return loader;
	}

	private static Set<String> getForceLoads(ClassLoader loader, String clazzName) {
		String classes = String.format("META-INF/vordel/forceLoad/%s", clazzName);
		URL forceLoad = loader.getResource(classes);
		Set<String> scanned = new HashSet<String>();

		scanned.add(clazzName);

		if (forceLoad != null) {
			try {
				InputStream in = forceLoad.openStream();

				try {
					BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

					try {
						String line = null;

						while ((line = reader.readLine()) != null) {
							line = line.trim();

							if (!line.isEmpty()) {
								scanned.add(line);
							}
						}
					} finally {
						reader.close();
					}

				} finally {
					in.close();
				}
			} catch (IOException e) {
				Trace.error(String.format("Got error reading module classes for '%s'", clazzName), e);
			}
		}

		return scanned;
	}

	private static Set<File> scanJavaArchives(File root, Set<File> scanned) {
		if ((root != null) && root.exists() && (scanned != null) && (!scanned.contains(root))) {
			if (root.isDirectory()) {
				File meta = new File(root, "META-INF");

				if (meta.exists() && meta.isDirectory()) {
					scanned.add(root);
				} else {
					for (File child : root.listFiles()) {
						if (!child.isFile()) {
							scanJavaArchives(child, scanned);
						}
					}

					for (File child : root.listFiles()) {
						if (child.isFile()) {
							scanJavaArchives(child, scanned);
						}
					}
				}
			} else if (isJavaArchive(root)) {
				scanned.add(root);
			}
		}

		return scanned;
	}

	private static boolean isJavaArchive(File root) {
		String name = root.getName();

		return root.isFile() && (name.endsWith(".jar") || name.endsWith(".zip"));
	}

	static void scanClasses(ConfigContext ctx, ClassLoader loader, Set<String> extensions) {
		List<Class<?>> scanned = scanExtensions(loader, extensions);

		registerClasses(ctx, scanned);
	}

	private static boolean isInstantiable(Class<?> clazz) {
		boolean isAbstract = Modifier.isAbstract(clazz.getModifiers());
		boolean isInstance = (clazz.getAnnotation(ExtensionInstance.class) != null) || (clazz.getAnnotation(ScriptExtension.class) != null);
		boolean isModule = ExtensionModule.class.isAssignableFrom(clazz);
		boolean hasEmptyContructor = false;

		try {
			clazz.getDeclaredConstructor();

			hasEmptyContructor = true;
		} catch (Exception e) {
			/* ignore */
		}

		return (!isAbstract) && ((isInstance && hasEmptyContructor) || isModule);
	}

	private static boolean isScriptExtension(Class<?> clazz) {
		boolean isAbstract = Modifier.isAbstract(clazz.getModifiers());
		boolean isInstance = clazz.getAnnotation(ScriptExtension.class) != null;
		boolean isEntension = AbstractScriptExtension.class.isAssignableFrom(clazz);
		boolean hasExtensionContructor = false;

		try {
			clazz.getDeclaredConstructor(AbstractScriptExtensionBuilder.class);

			hasExtensionContructor = true;
		} catch (Exception e) {
			/* ignore */
		}

		return (!isAbstract) && isInstance && isEntension && hasExtensionContructor;
	}

	public static void registerClasses(ConfigContext ctx, Iterable<Class<?>> clazzes) {
		if (clazzes != null) {
			List<Class<?>> sorted = new ArrayList<Class<?>>();

			for (Class<?> clazz : clazzes) {
				ExtensionContext plugin = clazz.getAnnotation(ExtensionContext.class);

				if ((plugin != null) || isInstantiable(clazz) || isScriptExtension(clazz)) {
					sorted.add(clazz);
				}
			}

			/* allow module load via Priority annotation */
			Collections.sort(sorted, PRIORITY_COMPARATOR);

			for (Class<?> clazz : sorted) {
				fromClass(ctx, clazz);
			}
		}
	}

	private static <T> ExtensionResourceProvider register(T script, Class<? extends T> clazz) {
		ExtensionResourceProvider resources = ExtensionResourceProvider.create(script, clazz);

		if (resources != null) {
			ExtensionContext annotation = clazz.getAnnotation(ExtensionContext.class);

			if (annotation != null) {
				String name = annotation.value();

				if (name.isEmpty()) {
					name = clazz.getName();
				}

				ExtensionLoader.registerExtensionContext(name, resources);

				Trace.info(String.format("registered class '%s' as extension '%s'", clazz.getName(), name));
			}
		}

		return resources;
	}
}
