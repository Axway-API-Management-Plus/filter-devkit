package com.vordel.circuit.filter.devkit.certmgr;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.context.resources.ContextResource;
import com.vordel.circuit.filter.devkit.context.resources.InvocableResource;
import com.vordel.circuit.filter.devkit.script.extension.AbstractScriptExtension;
import com.vordel.circuit.filter.devkit.script.extension.ScriptExtensionBuilder;
import com.vordel.circuit.filter.devkit.script.extension.annotations.ScriptExtension;

@ScriptExtension(KeyStoreExtensionRuntime.class)
public class KeyStoreExtensionModule extends AbstractScriptExtension implements KeyStoreExtensionRuntime {
	protected KeyStoreExtensionModule(ScriptExtensionBuilder builder) {
		super(builder);
	}

	@Override
	public KeyStoreResource getKeyStoreResource(String name) {
		ContextResource resource = getContextResource(name);

		return resource instanceof KeyStoreResource ? (KeyStoreResource) resource : null;
	}

	@Override
	public Boolean lockedCertStoreInvoke(Message msg, String name) throws CircuitAbortException {
		ContextResource resource = getContextResource(name);

		if (!(resource instanceof InvocableResource)) {
			throw new CircuitAbortException(String.format("resource '%s' is not invocable", name));
		}

		if (msg == null) {
			throw new CircuitAbortException("no message context");
		}

		return VordelKeyStore.invokeResourceSafely((InvocableResource) resource, msg);
	}
}
