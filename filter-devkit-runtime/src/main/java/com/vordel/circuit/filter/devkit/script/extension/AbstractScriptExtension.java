package com.vordel.circuit.filter.devkit.script.extension;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.context.resources.CacheResource;
import com.vordel.circuit.filter.devkit.context.resources.ContextResource;
import com.vordel.circuit.filter.devkit.context.resources.ContextResourceProvider;
import com.vordel.circuit.filter.devkit.context.resources.InvocableResource;
import com.vordel.circuit.filter.devkit.context.resources.KPSResource;
import com.vordel.circuit.filter.devkit.context.resources.SubstitutableResource;
import com.vordel.circuit.filter.devkit.script.advanced.AdvancedScriptRuntime;
import com.vordel.common.Dictionary;

/**
 * Base class for script extensions. This class allows the extension to interact
 * with the running script and to add {@link InvocableResource} or
 * {@link SubstitutableResource}
 * 
 * @author rdesaintleger@axway.com
 */
public abstract class AbstractScriptExtension implements AdvancedScriptRuntime {
	private final AdvancedScriptRuntime runtime;

	/**
	 * Protected contructor. Called by the script runtime binder. Once this class
	 * has been instanciated, reflected resources will be added to the calling
	 * script.
	 * 
	 * @param builder script extension builder
	 */
	protected AbstractScriptExtension(AbstractScriptExtensionBuilder builder) {
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
	public final String getFilterName() {
		return runtime.getFilterName();
	}
}
