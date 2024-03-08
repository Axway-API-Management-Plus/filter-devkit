package com.vordel.circuit.filter.devkit.script.advanced;

import javax.script.ScriptException;

import groovy.lang.Script;

/**
 * Additional top level functions exported to groovy runtime.
 * 
 * @author rdesaintleger@axway.com
 */
public interface GroovyScriptRuntime extends AdvancedScriptRuntime {
	/**
	 * bind annotated groovy methods to exportable context (usable in selectors and
	 * other scripts)
	 * 
	 * @param script current script instance
	 * @throws ScriptException if this method is called outside script attachment
	 *                         phase
	 */
	void reflectResources(Script script) throws ScriptException;

	/**
	 * scan a groovy script for invoke() and detach() with parameter injection. As a
	 * side effect CircuitAbortExceptions are not wrapped (since this is a direct
	 * Java reflective call).
	 * 
	 * @param script current script instance
	 * @throws ScriptException if this method is called outside script attachment
	 *                         phase
	 */
	void reflectEntryPoints(Script script) throws ScriptException;
}
