package com.vordel.circuit.filter.devkit.certmgr;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Enumeration;
import java.util.Iterator;

import com.vordel.trace.Trace;

public class JavaKeyStore extends KeyStoreMapper {

	public void reload(File path, char[] password, boolean keep) {
		if ((path != null) && path.isFile()) {
			Trace.info(String.format("Loading keystore '%s'", path.getAbsolutePath()));

			try {
				InputStream reader = new FileInputStream(path);

				try {
					KeyStore store = KeyStore.getInstance("JKS");

					store.load(reader, password);

					reload(store, password, keep);
				} finally {
					reader.close();
				}
			} catch (NoSuchAlgorithmException | KeyStoreException | CertificateException e) {
				Trace.error(String.format("JCE error loading keystore '%s'", path.getAbsolutePath()), e);
			} catch (IOException e) {
				Trace.error(String.format("unable to read keystore '%s'", path.getAbsolutePath()));
			}
		}
	}

	protected void reload(KeyStore store, char[] password, boolean keep) {
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
