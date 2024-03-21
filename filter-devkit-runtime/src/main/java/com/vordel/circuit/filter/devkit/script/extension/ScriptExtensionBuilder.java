package com.vordel.circuit.filter.devkit.script.extension;

import com.vordel.circuit.filter.devkit.script.context.ScriptContextRuntime;

/**
 * purpose of this class is to avoid implementers of script extensions to access
 * directly to the exported runtime.
 * 
 * @author rdesaintleger@axway.com
 */
public final class ScriptExtensionBuilder {
	final ScriptContextRuntime runtime;

	public ScriptExtensionBuilder(ScriptContextRuntime runtime) {
		this.runtime = runtime;
	}
}
