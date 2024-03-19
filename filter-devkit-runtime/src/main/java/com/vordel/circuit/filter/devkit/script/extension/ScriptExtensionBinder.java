package com.vordel.circuit.filter.devkit.script.extension;

import java.util.Set;

import javax.script.ScriptException;

import com.vordel.circuit.filter.devkit.script.context.ScriptContextBuilder;

@FunctionalInterface
public interface ScriptExtensionBinder {
	/**
	 * Adds resources from the given extension to the actual script. extensions can
	 * interact with the current script set of resources.
	 * 
	 * @param builder   instance of the current builder
	 * @param loaded    set of loaded classes for this builder
	 * @param className name of the extension Java class
	 * @throws ScriptException if this method is called outside script attachment
	 *                         phase or if extension does not exists.
	 */
	public void reflectExtension(ScriptContextBuilder builder, Set<String> loaded, String className) throws ScriptException;
}
