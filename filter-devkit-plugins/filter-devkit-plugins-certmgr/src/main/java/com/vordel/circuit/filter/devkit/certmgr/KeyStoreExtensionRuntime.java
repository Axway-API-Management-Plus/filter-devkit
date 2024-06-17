package com.vordel.circuit.filter.devkit.certmgr;

import java.util.function.BiFunction;

import com.vordel.security.cert.PersonalInfo;

public interface KeyStoreExtensionRuntime {
	KeyStoreResource getKeyStoreResource(String name);
	
	boolean importEntries(Iterable<KeyStoreEntry> entries, BiFunction<PersonalInfo, KeyStoreEntry, String> aliasGenerator, boolean useThread);
}
