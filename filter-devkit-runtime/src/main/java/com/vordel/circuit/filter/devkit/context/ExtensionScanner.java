package com.vordel.circuit.filter.devkit.context;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Priority;

import com.vordel.circuit.filter.devkit.context.annotations.ExtensionContextPlugin;
import com.vordel.config.ConfigContext;
import com.vordel.trace.Trace;

public class ExtensionScanner {
	private ExtensionScanner() {
	}

	private static void fromClass(ConfigContext ctx, Class<?> clazz) {
		ExtensionContextPlugin annotation = clazz.getAnnotation(ExtensionContextPlugin.class);
		Object instance = null;

		if (isModule(clazz)) {
			try {
				Constructor<?> contructor = clazz.getDeclaredConstructor();

				try {
					contructor.setAccessible(true);
					instance = contructor.newInstance();

					/* attach module */
					ExtensionLoader.registerModule(ctx, (ExtensionModule) instance);
				} finally {
					contructor.setAccessible(false);
				}
			} catch (InstantiationException e) {
				Trace.error(String.format("the class '%s' can't be instanciated", clazz.getName()), e);
			} catch (IllegalAccessException e) {
				Trace.error(String.format("the no-arg contructor for class '%s' is not accessible", clazz.getName()), e);
			} catch (InvocationTargetException e) {
				Throwable cause = e.getCause();

				Trace.error(String.format("got error instanciating class '%s'", clazz.getName()), cause);
			} catch (NoSuchMethodException e) {
				Trace.error(String.format("the class '%s' must have a no-arg contructor", clazz.getName()), e);
			}
		}

		if (annotation != null) {
			register(instance, clazz);
		}
	}

	public static ExtensionContext fromClass(Class<?> clazz) {
		return register(null, clazz);
	}

	public static <T> ExtensionContext fromInstance(T object) {
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

	private static List<Class<?>> scanExtensions(ClassLoader loader) {
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
			try {
				/*
				 * create classes which expose ExtensionContextPlugin annotation and implements
				 * ExtensionModulePlugin
				 */
				scanned.add(Class.forName(clazzName));
			} catch (Exception e) {
				Trace.error(String.format("Got exception loading class '%s'", clazzName), e);
			} catch (Error e) {
				Trace.error(String.format("Got error loading class '%s'", clazzName), e);
			}
		}

		return scanned;
	}

	public static void scanClasses(ConfigContext ctx, ClassLoader loader) {
		List<Class<?>> scanned = scanExtensions(loader);

		registerClasses(ctx, scanned);
	}

	private static boolean isModule(Class<?> clazz) {
		boolean isAbstract = Modifier.isAbstract(clazz.getModifiers());

		return (!isAbstract) && ExtensionModule.class.isAssignableFrom(clazz);
	}

	public static void registerClasses(ConfigContext ctx, Iterable<Class<?>> clazzes) {
		if (clazzes != null) {
			List<Class<?>> sorted = new ArrayList<Class<?>>();

			for (Class<?> clazz : clazzes) {
				ExtensionContextPlugin plugin = clazz.getAnnotation(ExtensionContextPlugin.class);

				if ((plugin != null) || isModule(clazz)) {
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

	public static List<File> getClassPath(ClassLoader loader, List<File> jars) {
		if (loader != null) {
			jars = getClassPath(loader.getParent(), jars);
		}

		if (loader instanceof URLClassLoader) {
			for (URL url : ((URLClassLoader) loader).getURLs()) {
				String protocol = url.getProtocol();

				if ("file".equals(protocol)) {
					try {
						File file = new File(url.toURI());

						if (file.isFile() && file.getName().endsWith(".jar") && (!jars.contains(file))) {
							jars.add(file);
						}
					} catch (URISyntaxException e) {
						Trace.error(String.format("URL '%s' can't be translated into a local file", url), e);
					}
				}
			}
		}

		return jars;
	}

	private static <T> ExtensionContext register(T script, Class<? extends T> clazz) {
		ExtensionContext resources = ExtensionContext.create(script, clazz);

		if (resources != null) {
			ExtensionContextPlugin annotation = clazz.getAnnotation(ExtensionContextPlugin.class);

			if (annotation != null) {
				String name = annotation.value();

				if (name.isEmpty()) {
					name = clazz.getName();
				}

				ExtensionLoader.registerGlobalResources(name, resources);

				Trace.info(String.format("registered class '%s' as extension '%s'", clazz.getName(), name));
			}
		}

		return resources;
	}
}
