package com.vordel.circuit.filter.devkit.certmgr;

import com.vordel.circuit.filter.devkit.context.resources.ContextResource;
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
}
