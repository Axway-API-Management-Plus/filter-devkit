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

import com.vordel.circuit.devkit.context.resources.SelectorResource;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionContextPlugin;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionModulePlugin;
import com.vordel.common.Dictionary;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.trace.Trace;

public class ExtensionScanner {
	private ExtensionScanner() {
	}

	private static void fromClass(ConfigContext ctx, Class<?> clazz) {
		ExtensionContextPlugin annotation = clazz.getAnnotation(ExtensionContextPlugin.class);
		Object instance = null;

		if (isInstantiable(clazz)) {
			try {
				Constructor<?> contructor = clazz.getDeclaredConstructor();

				try {
					contructor.setAccessible(true);
					instance = contructor.newInstance();

					/* attach module */
					ExtensionLoader.registerExtensionInstance(ctx, instance);
				} finally {
					contructor.setAccessible(false);
				}
			} catch (InstantiationException e) {
				Trace.error(String.format("the class '%s' can't be instantiated", clazz.getName()), e);
			} catch (IllegalAccessException e) {
				Trace.error(String.format("the no-arg contructor for class '%s' is not accessible", clazz.getName()), e);
			} catch (InvocationTargetException e) {
				Throwable cause = e.getCause();

				Trace.error(String.format("got error instantiating class '%s'", clazz.getName()), cause);
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
				 * create classes which expose ExtensionContextPlugin or ExtensionModulePlugin annotation
				 */
				scanned.add(Class.forName(clazzName, false, getClassLoader(loader, clazzName)));
			} catch (Exception e) {
				Trace.error(String.format("Got exception loading class '%s'", clazzName), e);
			} catch (Error e) {
				Trace.error(String.format("Got error loading class '%s'", clazzName), e);
			}
		}
		
		for(Class<?> loaded : scanned) {
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
	 * @param loader parent ClassLoader
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
			
			for(File file : scanned) {
				try {
					urls.add(file.toURI().toURL());
				} catch (MalformedURLException e) {
					Trace.error(String.format("can't normalize file path '%s' to URL", file.getAbsolutePath()), e);
				}
			}
			
			loader = new ExtensionClassLoader(clazzName, urls.toArray(new URL[0]), loader);
		}
		
		return loader;
	}

	public static Set<File> scanJavaArchives(File root, Set<File> scanned) {
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

	public static void scanClasses(ConfigContext ctx, ClassLoader loader) {
		List<Class<?>> scanned = scanExtensions(loader);

		registerClasses(ctx, scanned);
	}

	private static boolean isInstantiable(Class<?> clazz) {
		boolean isAbstract = Modifier.isAbstract(clazz.getModifiers());
		boolean isInstance = clazz.getAnnotation(ExtensionModulePlugin.class) != null;
		boolean isModule = ExtensionModule.class.isAssignableFrom(clazz);

		return (!isAbstract) && (isInstance || isModule);
	}

	public static void registerClasses(ConfigContext ctx, Iterable<Class<?>> clazzes) {
		if (clazzes != null) {
			List<Class<?>> sorted = new ArrayList<Class<?>>();

			for (Class<?> clazz : clazzes) {
				ExtensionContextPlugin plugin = clazz.getAnnotation(ExtensionContextPlugin.class);

				if ((plugin != null) || isInstantiable(clazz)) {
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

	private static <T> ExtensionContext register(T script, Class<? extends T> clazz) {
		ExtensionContext resources = ExtensionContext.create(script, clazz);

		if (resources != null) {
			ExtensionContextPlugin annotation = clazz.getAnnotation(ExtensionContextPlugin.class);

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
