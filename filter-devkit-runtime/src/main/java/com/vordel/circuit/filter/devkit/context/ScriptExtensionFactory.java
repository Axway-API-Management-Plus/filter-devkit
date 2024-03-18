package com.vordel.circuit.filter.devkit.context;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.codehaus.groovy.runtime.MethodClosure;

import com.vordel.circuit.filter.devkit.context.resources.ContextResource;
import com.vordel.circuit.filter.devkit.script.ScriptRuntime;
import com.vordel.circuit.filter.devkit.script.advanced.AdvancedScriptRuntime;
import com.vordel.circuit.filter.devkit.script.advanced.AdvancedScriptRuntimeBinder;
import com.vordel.circuit.filter.devkit.script.extension.AbstractScriptExtensionBuilder;

import groovy.lang.Script;

public abstract class ScriptExtensionFactory {
	public abstract Object createExtensionInstance(AbstractScriptExtensionBuilder builder) throws ScriptException;

	public abstract Object proxify(Object instance);

	public abstract void scanScriptExtension(List<Method> methods);

	public final void bind(Map<String, ContextResource> resources, ScriptEngine engine, AdvancedScriptRuntime runtime) throws ScriptException {
		AdvancedScriptRuntimeBinder binder = AdvancedScriptRuntimeBinder.getScriptBinder(engine);

		if (binder == null) {
			throw new ScriptException("Unsupported script engine");
		}

		AbstractScriptExtensionBuilder builder = new AbstractScriptExtensionBuilder(runtime);
		Object instance = createExtensionInstance(builder);
		List<Method> methods = new ArrayList<Method>();

		/* reflect invocables/substitutables and extension functions */
		ExtensionResourceProvider.reflectInstance(resources, instance);

		scanScriptExtension(methods);

		/* bind proxy interface to script */
		binder.bind(engine, proxify(instance), methods.toArray(new Method[0]));
	}

	public final void bind(Map<String, ContextResource> resources, Script script, ScriptRuntime runtime) throws ScriptException {
		AbstractScriptExtensionBuilder builder = new AbstractScriptExtensionBuilder(runtime);
		Object instance = createExtensionInstance(builder);
		List<Method> methods = new ArrayList<Method>();

		/* reflect invocables/substitutables and extension functions */
		ExtensionResourceProvider.reflectInstance(resources, instance);

		scanScriptExtension(methods);

		/* proxify instance for debug traces */
		Object proxy = proxify(instance);

		for (Method method : methods) {
			String name = method.getName();
			MethodClosure closure = new MethodClosure(proxy, name);

			script.setProperty(name, closure);
		}
	}

	protected static final Object proxify(Object instance, ClassLoader loader, Class<?>... clazzes) {
		ScriptExtensionHandler handler = new ScriptExtensionHandler(instance);

		return Proxy.newProxyInstance(loader, clazzes, handler);
	}

	/**
	 * scan all super interfaces for a given script extension
	 * 
	 * @param methods aggregated methods for all super interfaces found
	 * @param clazzes classes to be scanned
	 */
	protected static final void scanScriptExtensionInterfaces(List<Method> methods, Class<?>... clazzes) {
		Set<Method> seen = new HashSet<Method>();

		for (Class<?> clazz : clazzes) {
			scanScriptExtensionInterface(methods, clazz, clazz, seen);
		}
	}

	/**
	 * scan all super interfaces for a given script extension. ignoring overidden
	 * methods
	 * 
	 * @param methods aggregated methods for all super interfaces found
	 * @param base    base interface
	 * @param clazz   current interface
	 * @param seen    methods which are already aggregated
	 */
	private static final void scanScriptExtensionInterface(List<Method> methods, Class<?> base, Class<?> clazz, Set<Method> seen) {
		Class<?> superClazz = clazz.getSuperclass();
		Class<?>[] interfaces = clazz.getInterfaces();

		for (Method method : clazz.getDeclaredMethods()) {
			try {
				/* retrieve base method according to base class */
				method = base.getMethod(method.getName(), method.getParameterTypes());
			} catch (NoSuchMethodException e) {
				/* ignore */
			}

			if (seen.add(method)) {
				methods.add(method);
			}
		}

		for (Class<?> impl : interfaces) {
			scanScriptExtensionInterface(methods, base, impl, seen);
		}

		if ((superClazz != null) && (superClazz.isInterface())) {
			scanScriptExtensionInterface(methods, base, superClazz, seen);
		}
	}
}
