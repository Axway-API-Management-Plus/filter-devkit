package com.vordel.circuit.filter.devkit.script.extension;

import javax.script.ScriptException;

import com.vordel.circuit.filter.devkit.script.context.ScriptContextBuilder;

/**
 * Base class for script extensions which need to attach resources to a script.
 * 
 * @author rdesaintleger@axway.com
 */
public abstract class ScriptExtensionConfigurator {
	/**
	 * Additional entry point so extension can attach resources before extension is
	 * added to the script. This method is called for each script even if the
	 * extension is a singleton.
	 * 
	 * @param builder resource builder the calling script
	 * @throws ScriptException if any error occurs
	 */
	protected abstract void attachResources(ScriptContextBuilder builder) throws ScriptException;
}
