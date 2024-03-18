package com.vordel.circuit.filter.devkit.script.advanced;

import java.util.function.Consumer;

import javax.script.ScriptException;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.context.ExtensionLoader;
import com.vordel.circuit.filter.devkit.script.ScriptContextBuilder;
import com.vordel.config.Circuit;

/**
 * Configuration functions for advanced scripts.
 * 
 * @author rdesaintleger@axway.com
 */
public interface AdvancedScriptConfigurator {
	/**
	 * By default, exceptions thrown by script filter will be enclosed in
	 * ScriptException. This behavior can be changed and CircuitException can be
	 * thrown unwrapped. Unwrapping also takes place on Groovy Scripts which use
	 * script extensions.
	 * 
	 * @param unwrap {@code true} to unwrap {@link CircuitAbortException},
	 *               {@code false} for default behavior.
	 * @throws ScriptException if this method is called outside script attachment
	 *                         phase
	 */
	void setUnwrapCircuitAbortException(boolean unwrap) throws ScriptException;

	/**
	 * By default, the script invoke function is called only with the message
	 * parameter. the given parameter allows the {@link Circuit} to be passed as
	 * first argument (like in filters).
	 * 
	 * @param extended if {@code false}, invoke is called with {@link Message} as
	 *                 single parameter (Ex: {@code invoke(message)}). if
	 *                 {@code true}, {@link Circuit} and {@link Message} are used
	 *                 respectively as first and second parameter (Ex:
	 *                 {@code invoke(circuit, message)})
	 * @throws ScriptException if this method is called outside script attachment
	 *                         phase
	 */
	void setExtendedInvoke(boolean extended) throws ScriptException;

	/**
	 * Adds resources from the given extension to the actual script. extensions can
	 * interact with the current script set of resources. If {@link ExtensionLoader}
	 * is not activated script extension interfaces are not available (in this case
	 * use binary name of implementation instead.
	 * 
	 * @param extension binary name of the extension Java class
	 * @throws ScriptException if this method is called outside script attachment
	 *                         phase or if extension does not exists.
	 */
	void reflectExtension(String extension) throws ScriptException;

	/**
	 * Attach resources to a script using legacy mechanisms
	 * 
	 * @param configurator consumer for {@link ScriptContextBuilder}
	 * @throws ScriptException if called outside the attach call
	 */
	void attachResources(Consumer<ScriptContextBuilder> configurator) throws ScriptException;
}
