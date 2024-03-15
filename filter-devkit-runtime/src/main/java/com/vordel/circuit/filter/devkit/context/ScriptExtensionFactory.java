package com.vordel.circuit.filter.devkit.context;

import java.lang.reflect.Proxy;

import javax.script.ScriptException;

import com.vordel.circuit.filter.devkit.script.extension.AbstractScriptExtensionBuilder;

public abstract class ScriptExtensionFactory {
	public abstract Object createExtensionInstance(AbstractScriptExtensionBuilder builder) throws ScriptException;

	public abstract Class<?> getExtensionInterface();

	public final Object proxify(Object instance) {
		Class<?> clazz = getExtensionInterface();
		ClassLoader loader = clazz.getClassLoader();
		ScriptExtensionHandler handler = new ScriptExtensionHandler(instance);

		return Proxy.newProxyInstance(loader, new Class<?>[] { clazz }, handler);
	}
}
