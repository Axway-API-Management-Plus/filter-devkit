package com.vordel.circuit.filter.devkit.script.advanced;

import com.vordel.circuit.filter.devkit.script.context.ScriptContextRuntime;

/**
 * Base top level functions exported to all scripts runtime.
 * 
 * @author rdesaintleger@axway.com
 */
public interface AdvancedScriptRuntime extends ScriptContextRuntime {
	/**
	 * This method allow to retrieve Filter name for debugging purposes
	 * 
	 * @return Filter name for this script
	 */
	String getFilterName();
}
