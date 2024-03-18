package com.vordel.circuit.filter.devkit.script;

import javax.script.ScriptException;

import com.vordel.circuit.filter.devkit.context.ExtensionLoader;

@FunctionalInterface
public interface ScriptExtensionBinder {
	/**
	 * Adds resources from the given extension to the actual script. extensions can
	 * interact with the current script set of resources. If {@link ExtensionLoader}
	 * is not activated script extension interfaces are not available (in this case
	 * use binary name of implementation instead.
	 * 
	 * @param className binary name of the extension Java class
	 * @throws ScriptException if this method is called outside script attachment
	 *                         phase or if extension does not exists.
	 */
	public void reflectExtension(String className) throws ScriptException;
}
