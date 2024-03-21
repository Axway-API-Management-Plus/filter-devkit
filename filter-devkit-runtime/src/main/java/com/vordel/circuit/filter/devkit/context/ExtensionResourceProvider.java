package com.vordel.circuit.filter.devkit.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.vordel.circuit.filter.devkit.context.resources.AbstractContextResourceProvider;
import com.vordel.circuit.filter.devkit.context.resources.ContextResource;
import com.vordel.circuit.filter.devkit.context.resources.ContextResourceProvider;
import com.vordel.circuit.filter.devkit.context.resources.JavaMethodResource;

/**
 * This class represent exported resources from Java (and Groovy) code. It will
 * expose annotated methods to the API Gateway using the
 * {@link ContextResourceProvider} interface. Exposed methods are discovered
 * using basic reflection.
 * 
 * @author rdesaintleger@axway.com
 */
public final class ExtensionResourceProvider extends AbstractContextResourceProvider {
	private final Map<String, ContextResource> resources;
	private final Class<?> clazz;

	/**
	 * Private constructor. Called when exposed methods has been discovered
	 * 
	 * @param clazz     class which has been reflected
	 * @param resources exposed resources for this class.
	 */
	private ExtensionResourceProvider(Class<?> clazz, Map<String, ContextResource> resources) {
		this.resources = resources;
		this.clazz = clazz;
	}

	/**
	 * package private create() call. Used by the {@link ExtensionScanner}. This
	 * method will reflect provided class parameter and will create resources
	 * according to the given instance.
	 * 
	 * @param <T>    type parameter used to link the provided instance and provided
	 *               class
	 * @param module module instance to be reflected
	 * @param clazz  class definition of the module instance
	 * @return ExtensionResourceProvider containing exposed methods for the given
	 *         module
	 */
	static <T> ExtensionResourceProvider create(T module, Class<? extends T> clazz) {
		Map<String, ContextResource> resources = new HashMap<String, ContextResource>();

		JavaMethodResource.reflectInstance(resources, module, clazz);

		return new ExtensionResourceProvider(clazz, Collections.unmodifiableMap(resources));
	}

	@Override
	public final ContextResource getContextResource(String name) {
		return resources.get(name);
	}

	/**
	 * @return the class loader used for the underlying class
	 */
	public final ClassLoader getClassLoader() {
		return clazz.getClassLoader();
	}
}
