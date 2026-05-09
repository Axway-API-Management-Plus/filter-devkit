package com.vordel.circuit.filter.devkit.script.extension;

import javax.script.ScriptException;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.context.resources.CacheResource;
import com.vordel.circuit.filter.devkit.context.resources.ContextResource;
import com.vordel.circuit.filter.devkit.context.resources.ContextResourceProvider;
import com.vordel.circuit.filter.devkit.context.resources.FunctionResource;
import com.vordel.circuit.filter.devkit.context.resources.InvocableResource;
import com.vordel.circuit.filter.devkit.context.resources.KPSResource;
import com.vordel.circuit.filter.devkit.context.resources.SubstitutableResource;
import com.vordel.circuit.filter.devkit.script.context.ScriptContextBuilder;
import com.vordel.circuit.filter.devkit.script.context.ScriptContextRuntime;
import com.vordel.common.Dictionary;

/**
 * Base class for script extensions which needs to interact with script
 * resources.
 * 
 * @author rdesaintleger@axway.com
 */
public abstract class AbstractScriptExtension extends ScriptExtensionConfigurator implements ScriptContextRuntime {
	private final ScriptContextRuntime runtime;

	/**
	 * Protected constructor. Called by the script runtime binder. Once this class
	 * has been instanciated, reflected resources will be added to the calling
	 * script.
	 * 
	 * @param builder script extension builder
	 */
	protected AbstractScriptExtension(ScriptExtensionBuilder builder) {
		this.runtime = builder.runtime;
	}

	@Override
	public final ContextResource getContextResource(String name) {
		return runtime.getContextResource(name);
	}

	@Override
	public final InvocableResource getInvocableResource(String name) {
		return runtime.getInvocableResource(name);
	}

	@Override
	public final FunctionResource getFunctionResource(String name) {
		return runtime.getFunctionResource(name);
	}

	@Override
	public final SubstitutableResource<?> getSubstitutableResource(String name) {
		return runtime.getSubstitutableResource(name);
	}

	@Override
	public final KPSResource getKPSResource(String name) {
		return runtime.getKPSResource(name);
	}

	@Override
	public final CacheResource getCacheResource(String name) {
		return runtime.getCacheResource(name);
	}

	@Override
	public final Boolean invokeResource(Message msg, String name) throws CircuitAbortException {
		return runtime.invokeResource(msg, name);
	}

	@Override
	public final Object substituteResource(Dictionary dict, String name) {
		return runtime.substituteResource(dict, name);
	}

	@Override
	public final ContextResourceProvider getExportedResources() {
		return runtime.getExportedResources();
	}

	@Override
	protected void attachResources(ScriptContextBuilder builder) throws ScriptException {
		/* no resources to bind */
	}
}
