package com.vordel.circuit.filter.devkit.script;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.context.resources.CacheResource;
import com.vordel.circuit.filter.devkit.context.resources.ContextResource;
import com.vordel.circuit.filter.devkit.context.resources.FunctionResource;
import com.vordel.circuit.filter.devkit.context.resources.InvocableResource;
import com.vordel.circuit.filter.devkit.context.resources.KPSResource;
import com.vordel.circuit.filter.devkit.context.resources.SubstitutableResource;
import com.vordel.common.Dictionary;
import com.vordel.trace.Trace;

/**
 * base runtime implementation for scripts. Used for regular and advanced scripts
 * 
 * @author rdesaintleger@axway.com
 */
public abstract class ScriptContext implements ScriptRuntime {
	@Override
	public final InvocableResource getInvocableResource(String name) {
		ContextResource resource = getContextResource(name);

		return resource instanceof InvocableResource ? (InvocableResource) resource : null;
	}

	@Override
	public final FunctionResource getFunctionResource(String name) {
		ContextResource resource = getContextResource(name);

		return resource instanceof FunctionResource ? (FunctionResource) resource : null;
	}

	@Override
	public final SubstitutableResource<?> getSubstitutableResource(String name) {
		ContextResource resource = getContextResource(name);

		return resource instanceof SubstitutableResource ? (SubstitutableResource<?>) resource : null;
	}

	@Override
	public final KPSResource getKPSResource(String name) {
		ContextResource resource = getContextResource(name);

		return resource instanceof KPSResource ? (KPSResource) resource : null;
	}

	@Override
	public final CacheResource getCacheResource(String name) {
		ContextResource resource = getContextResource(name);

		return resource instanceof CacheResource ? (CacheResource) resource : null;
	}

	@Override
	public final Boolean invokeResource(Message msg, String name) throws CircuitAbortException {
		ContextResource resource = getContextResource(name);
		Boolean result = null;

		if (resource != null) {
			if (!(resource instanceof InvocableResource)) {
				throw new CircuitAbortException(String.format("resource '%s' is not invocable", name));
			}

			if (msg == null) {
				throw new CircuitAbortException("no message context");
			}

			result = ((InvocableResource) resource).invoke(msg);
		}

		return result;
	}

	@Override
	public final Object substituteResource(Dictionary dict, String name) {
		ContextResource resource = getContextResource(name);
		Object result = null;

		if (resource instanceof SubstitutableResource) {
			result = ((SubstitutableResource<?>) resource).substitute(dict);
		} else {
			Trace.error(String.format("resource '%s' is not substitutable", name));
		}

		return result;
	}
}
