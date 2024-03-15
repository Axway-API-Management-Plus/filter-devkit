package com.vordel.circuit.filter.devkit.dynamic.compiler;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import javax.tools.ForwardingJavaFileManager;
import javax.tools.StandardJavaFileManager;

public class CompilerFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
	private final AggregatedClassLoader loader;

	public CompilerFileManager(StandardJavaFileManager sjfm, ClassLoader xcl, ClassLoader tools) {
		super(sjfm);

		this.loader = new AggregatedClassLoader(xcl, tools);
	}

	public ClassLoader getClassLoader(Location location) {
		return loader;
	}

	private static final class AggregatedClassLoader extends ClassLoader {
		private final ClassLoader xcl;
		private final ClassLoader tools;

		private AggregatedClassLoader(ClassLoader xcl, ClassLoader tools) {
			this.xcl = xcl;
			this.tools = tools;
		}
		@Override
		public Class<?> loadClass(String name) throws ClassNotFoundException {
			try {
				return xcl.loadClass(name);
			} catch (ClassNotFoundException e) {
				return tools.loadClass(name);
			}
		}

		@Override
		public URL getResource(String name) {
			URL resource = xcl.getResource(name);

			if (resource == null) {
				resource = tools.getResource(name);
			}

			return resource;
		}

		@Override
		public Enumeration<URL> getResources(String name) throws IOException {
			Vector<URL> resources = new Vector<URL>();
			Set<URL> seen = new HashSet<URL>();

			add(resources, xcl.getResources(name), seen);
			add(resources, tools.getResources(name), seen);

			return resources.elements();
		}

		private void add(Vector<URL> resources, Enumeration<URL> urls, Set<URL> seen) {
			while (urls.hasMoreElements()) {
				URL url = urls.nextElement();

				if (seen.add(url)) {
					resources.add(url);
				}
			}
		}
	}
}