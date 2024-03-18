package com.vordel.circuit.filter.devkit.script.extension;

import com.vordel.circuit.filter.devkit.script.ScriptRuntime;

/**
 * purpose of this class is to avoid implementers of script extensions to access
 * directly to the exported runtime.
 * 
 * @author rdesaintleger@axway.com
 */
public final class AbstractScriptExtensionBuilder {
	final ScriptRuntime runtime;

	public AbstractScriptExtensionBuilder(ScriptRuntime runtime) {
		this.runtime = runtime;
	}
}
