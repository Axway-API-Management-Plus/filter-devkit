package com.vordel.circuit.script.bind;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.annotation.Priority;

import com.vordel.circuit.ext.filter.quick.QuickFilterType;
import com.vordel.circuit.ext.filter.quick.QuickJavaFilterDefinition;
import com.vordel.circuit.script.context.MessageContextModule;
import com.vordel.config.ConfigContext;
import com.vordel.trace.Trace;

public class ExtensionScanner {
	private ExtensionScanner() {
	}

	private static void fromClass(ConfigContext ctx, Class<?> clazz) {
		ExtensionPlugin annotation = clazz.getAnnotation(ExtensionPlugin.class);
		Object instance = null;

		if (isFilter(clazz)) {
			MessageContextModule.registerQuickJavaFilter(clazz);
		}

		if (isModule(clazz)) {
			try {
				Constructor<?> contructor = clazz.getDeclaredConstructor();

				try {
					contructor.setAccessible(true);
					instance = contructor.newInstance();

					/* attach module */
					MessageContextModule.registerModule(ctx, (ExtensionModule) instance);
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
	
	public static List<Class<?>> scanClasses(ClassLoader loader) {
		Set<String> clazzes = new HashSet<String>();

		try {
			ExtensionAcceptingListener listener = new ExtensionAcceptingListener(clazzes, ExtensionPlugin.class, ExtensionModule.class, QuickFilterType.class, QuickJavaFilterDefinition.class);

			/* scan current classpath for annotations and interfaces */
			scanClasses(loader, listener);

			/* apply inheritance to discover more modules */
			listener.processInheritance();
		} catch (Exception e) {
			Trace.error("Unable to scan class path", e);
		}

		List<Class<?>> scanned = new ArrayList<Class<?>>();

		for (String clazzName : clazzes) {
			try {
				/*
				 * create classes which expose ExtensionPlugin annotation and implements ExtensionModule
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
		List<Class<?>> scanned = scanClasses(loader);

		registerClasses(ctx, scanned);
	}

	private static boolean isModule(Class<?> clazz) {
		boolean isAbstract = Modifier.isAbstract(clazz.getModifiers());

		return (!isAbstract) && ExtensionModule.class.isAssignableFrom(clazz);
	}

	public static boolean isFilter(Class<?> clazz) {
		boolean isAbstract = Modifier.isAbstract(clazz.getModifiers());
		QuickFilterType filterType = clazz.getAnnotation(QuickFilterType.class);

		return (filterType != null) && (!isAbstract) && QuickJavaFilterDefinition.class.isAssignableFrom(clazz);
	}

	public static void registerClasses(ConfigContext ctx, Iterable<Class<?>> clazzes) {
		if (clazzes != null) {
			List<Class<?>> sorted = new ArrayList<Class<?>>();

			for (Class<?> clazz : clazzes) {
				ExtensionPlugin plugin = clazz.getAnnotation(ExtensionPlugin.class);

				if ((plugin != null) || isFilter(clazz) || isModule(clazz)) {
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

	public static void scanClasses(ClassLoader loader, ExtensionAcceptingListener listener) {
		for (File file : getClassPath(loader, new ArrayList<File>())) {
			try {
				Trace.debug(String.format("scanning %s for ExtensionPlugin annotation and ExtensionModule interface", file.getAbsolutePath()));

				JarFile jar = new JarFile(file);

				try {
					scanClasses(jar, listener);
				} finally {
					jar.close();
				}
			} catch (IOException e) {
				Trace.error(String.format("got I/O exception scanning '%s'", file.getAbsolutePath()), e);
			}
		}
	}

	public static void scanClasses(JarFile jar, ExtensionAcceptingListener listener) throws IOException {
		for (Enumeration<JarEntry> iterator = jar.entries(); iterator.hasMoreElements();) {
			JarEntry item = iterator.nextElement();
			String name = item.getName();

			if (name.endsWith(".class")) {
				InputStream is = jar.getInputStream(item);

				try {
					listener.process(is);
				} catch(RuntimeException e) {
					Trace.debug(String.format("skipping class '%s'", name));
				} finally {
					is.close();
				}
			}
		}
	}

	private static <T> ExtensionContext register(T script, Class<? extends T> clazz) {
		ExtensionContext resources = ExtensionContext.create(script, clazz);

		if (resources != null) {
			ExtensionPlugin annotation = clazz.getAnnotation(ExtensionPlugin.class);

			if (annotation != null) {
				String name = annotation.value();

				if (name.isEmpty()) {
					name = clazz.getName();
				}

				MessageContextModule.registerGlobalResources(name, resources);

				Trace.info(String.format("registered class '%s' as extension '%s'", clazz.getName(), name));
			}
		}

		return resources;
	}
}
