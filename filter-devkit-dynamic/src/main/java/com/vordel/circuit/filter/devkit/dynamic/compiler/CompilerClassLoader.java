package com.vordel.circuit.filter.devkit.dynamic.compiler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vordel.trace.Trace;

public class CompilerClassLoader extends ClassLoader {
	private final Map<String, DynamicByteCode> code = new HashMap<String, DynamicByteCode>();
	private final Map<String, Class<?>> defined = new HashMap<String, Class<?>>();
	private final URLClassLoader resources;

	public CompilerClassLoader(ClassLoader parent, File root) {
		super(parent);

		this.resources = createResourceLoader(root, parent);
	}

	private static URLClassLoader createResourceLoader(File root, ClassLoader parent) {
		URLClassLoader loader = null;

		if (root != null) {
			try {
				loader = parent == null ? new URLClassLoader(new URL[] { root.toURI().toURL() }) : new URLClassLoader(new URL[] { root.toURI().toURL() }, parent);
			} catch (MalformedURLException e) {
				Trace.error("can't normalize class root directory to URL", e);
			}
		}

		return loader;
	}

	public CompilerClassLoader(File root) {
		super();

		this.resources = createResourceLoader(root, null);
	}

	protected void defineClass(String name, DynamicByteCode mbc) {
		name = name.replace("/", ".");

		code.put(name, mbc);
	}

	protected Class<?> findClass(String name) throws ClassNotFoundException {
		DynamicByteCode mbc = code.get(name);
		Class<?> result = null;

		if (mbc == null) {
			result = super.findClass(name);
		} else {
			result = defineClass(name, mbc.getBytes(), 0, mbc.getBytes().length);

			defined.put(name, result);
		}

		return result;
	}

	@Override
	public URL getResource(String name) {
		return resources.getResource(name);
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		return resources.getResources(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		return resources.getResourceAsStream(name);
	}

	@Override
	protected URL findResource(String name) {
		return resources.findResource(name);
	}

	@Override
	protected Enumeration<URL> findResources(String name) throws IOException {
		return resources.findResources(name);
	}

	public List<Class<?>> loadClasses(List<Class<?>> classes) {
		for (String name : code.keySet()) {
			Class<?> clazz = findLoadedClass(name);

			try {
				if (clazz == null) {
					clazz = findClass(name);
				}

				if (!classes.contains(clazz)) {
					classes.add(clazz);
				}
			} catch (ClassNotFoundException e) {
				Trace.error(String.format("Unable to load class '%s'", name), e);
			}
		}

		return classes;
	}
}
