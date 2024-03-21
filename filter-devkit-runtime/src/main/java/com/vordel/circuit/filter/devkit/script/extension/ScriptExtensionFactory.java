package com.vordel.circuit.filter.devkit.script.extension;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.script.ScriptException;

import com.vordel.circuit.filter.devkit.script.context.ScriptContextBuilder;
import com.vordel.circuit.filter.devkit.script.context.ScriptContextRuntime;

public abstract class ScriptExtensionFactory {
	protected abstract Object createExtensionInstance(ScriptExtensionBuilder builder) throws ScriptException;

	public abstract Object proxify(Object instance);

	public abstract void scanScriptExtension(List<Method> methods);
	
	public abstract boolean isLoaded(Set<String> loaded);

	public final Object createExtensionInstance(ScriptContextBuilder builder, ScriptContextRuntime runtime) throws ScriptException {
		ScriptExtensionBuilder holder = new ScriptExtensionBuilder(runtime);
		Object instance = createExtensionInstance(holder);

		if (instance instanceof ScriptExtensionConfigurator) {
			((ScriptExtensionConfigurator) instance).attachResources(builder);
		}

		return instance;
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
