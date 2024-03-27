package com.vordel.circuit.filter.devkit.certmgr;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Enumeration;
import java.util.Iterator;

import com.vordel.trace.Trace;

public class JavaKeyStore extends KeyStoreMapper {
	protected void reload(KeyStore store, char[] password) {
		try {
			reload(new Iterator<String>() {
				private final Enumeration<String> aliases = store.aliases();

				@Override
				public boolean hasNext() {
					return aliases.hasMoreElements();
				}

				@Override
				public String next() {
					return aliases.nextElement();
				}
			}, (alias) -> {
				return new KeyStoreEntry(store, alias, password);
			});
		} catch (KeyStoreException e) {
			Trace.error("keystore has not been initialized", e);
		}
	}
}
