package com.vordel.circuit.filter.devkit.certmgr;

import java.util.function.BiFunction;

import com.vordel.circuit.filter.devkit.context.resources.ContextResource;
import com.vordel.circuit.filter.devkit.script.extension.AbstractScriptExtension;
import com.vordel.circuit.filter.devkit.script.extension.ScriptExtensionBuilder;
import com.vordel.circuit.filter.devkit.script.extension.annotations.ScriptExtension;
import com.vordel.security.cert.PersonalInfo;

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
	public boolean importEntries(Iterable<KeyStoreEntry> entries, BiFunction<PersonalInfo, KeyStoreEntry, String> aliasGenerator, boolean useThread) {
		VordelKeyStore store = VordelKeyStore.getInstance();

		return store.importEntries(entries, aliasGenerator, useThread);
	}
}
