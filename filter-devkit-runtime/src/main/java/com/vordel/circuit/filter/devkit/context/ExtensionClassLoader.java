package com.vordel.circuit.filter.devkit.context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * child first class loader for extensions. This class loader allows to specify
 * external directories for classes which are isolated from API Gateway.
 * 
 * @author rdesaintleger@axway.com
 */
public class ExtensionClassLoader extends URLClassLoader {
	/**
	 * set of classes which must be loaded locally. also map qualified names to binary names
	 */
	private final Map<String,String> forceLocal = new HashMap<String,String>();

	public ExtensionClassLoader(URL[] urls, ClassLoader parent) {
		this(Collections.emptySet(), urls, parent);
	}

	public ExtensionClassLoader(String forceLocal, URL[] urls, ClassLoader parent) {
		this(Collections.singleton(forceLocal), urls, parent);
	}

	public ExtensionClassLoader(Set<String> forceLocal, URL[] urls, ClassLoader parent) {
		super(urls, parent);

		for(String binaryName : forceLocal) {
			String qualifiedName = binaryName.replace('$', '.');

			this.forceLocal.put(binaryName, binaryName);
			this.forceLocal.put(qualifiedName, binaryName);
		}
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			Class<?> loaded = findLoadedClass(name);

			if (loaded == null) {
				try {
					/* find class from local jars/directories */
					loaded = findClass(name);
				} catch (ClassNotFoundException e) {
					String binaryName = getLocalClassName(name);

					if (binaryName != null) {
						/*
						 * class not found. try to retrieve class file in parent classpath and define in
						 * this classloader
						 */
						loaded = findParentClassAsLocal(binaryName);

						if (loaded == null) {
							throw e;
						}
					} else {
						/* otherwise try parent to load class */
						loaded = super.loadClass(name, resolve);
					}
				}
			}

			if (resolve) { // marked to resolve
				resolveClass(loaded);
			}

			return loaded;
		}
	}

	private String getLocalClassName(String name) {
		String local = forceLocal.get(name);

		if (local == null) {
			int inner = name.lastIndexOf('$');

			if (inner > -1) {
				/* check if enclosing class is handled by this ClassLoader */
				String enclosing = getLocalClassName(name.substring(0, inner));

				if (enclosing != null) {
					return name;
				}
			}
		}

		return local;
	}

	private Class<?> findParentClassAsLocal(String name) throws ClassNotFoundException {
		/*
		 * class must be loaded in THIS classloader. search for the requested class file
		 */
		String path = name.replace('.', '/').concat(".class");
		URL url = getResource(path);

		Class<?> loaded = null;

		if (url == null) {
			/* if not found try system classpath */
			url = getSystemResource(name);
		}

		if (url != null) {
			try {
				/* if class stream has been found, define it locally */
				byte[] buffer = toByteArray(url);

				loaded = defineClass(name, buffer, 0, buffer.length);
			} catch (IOException e) {
				throw new ClassNotFoundException(String.format("Unable to read class stream for '%s'", name), e);
			}
		}

		return loaded;
	}

	private static byte[] toByteArray(URL url) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		InputStream in = url.openStream();

		try {
			byte[] buffer = new byte[1024];
			int read = 0;

			while ((read = (in.read(buffer, 0, buffer.length))) > 0) {
				out.write(buffer, 0, read);
			}
		} finally {
			in.close();
		}

		return out.toByteArray();
	}
}
