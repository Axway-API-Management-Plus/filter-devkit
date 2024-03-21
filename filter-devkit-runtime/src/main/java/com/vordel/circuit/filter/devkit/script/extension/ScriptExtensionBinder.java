package com.vordel.circuit.filter.devkit.script.extension;

import java.lang.reflect.Method;

import javax.script.ScriptException;

@FunctionalInterface
public interface ScriptExtensionBinder {
	/**
	 * Adds resources from the given extension to the actual script. extensions can
	 * interact with the current script set of resources.
	 * 
	 * @param className requested extension name
	 * @param instance extension proxy to be bound
	 * @param methods list of proxy exported methods
	 * @throws ScriptException if an error occurs
	 */
	public void bindExtension(String className, Object instance, Method[] methods) throws ScriptException;
}
