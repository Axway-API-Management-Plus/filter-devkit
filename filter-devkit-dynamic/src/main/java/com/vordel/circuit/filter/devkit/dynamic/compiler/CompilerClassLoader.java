package com.vordel.circuit.filter.devkit.dynamic.compiler;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vordel.circuit.filter.devkit.context.ExtensionClassLoader;
import com.vordel.trace.Trace;

public class CompilerClassLoader extends ExtensionClassLoader {
	private final Map<String, DynamicByteCode> code = new HashMap<String, DynamicByteCode>();

	public CompilerClassLoader(File root, ClassLoader parent) {
		super(toURLs(root), parent);
	}

	protected void defineClass(String name, DynamicByteCode mbc) {
		name = name.replace("/", ".");

		code.put(name, mbc);
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		DynamicByteCode mbc = code.remove(name);
		Class<?> result = null;

		if (mbc == null) {
			result = super.findClass(name);
		} else {
			result = defineClass(name, mbc.getBytes(), 0, mbc.getBytes().length);
		}

		return result;
	}

	public List<Class<?>> loadClasses(List<Class<?>> classes) {
		Set<String> defined = new HashSet<String>(code.keySet());
		
		for (String name : defined) {
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
	
	private static URL[] toURLs(File root) {
		try {
			return Collections.singleton(root.toURI().toURL()).toArray(new URL[1]);
		} catch (MalformedURLException e) {
			throw new IllegalStateException(String.format("can't normalize file path '%s' to URL", root.getAbsolutePath()), e);
		}
	}
}
