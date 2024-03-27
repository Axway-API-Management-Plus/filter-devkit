package com.vordel.circuit.filter.devkit.certmgr;

import java.security.PublicKey;
import java.util.Iterator;
import java.util.List;

import com.nimbusds.jose.jwk.JWK;

public final class KeyStoreHolder extends KeyStoreResource {
	public final KeyStoreResource store;
	private int rcnt;

	public static KeyStoreHolder fromKeyStoreResource(KeyStoreResource resource) {
		if (resource instanceof KeyStoreHolder) {
			return (KeyStoreHolder) resource;
		}

		return new KeyStoreHolder(resource);
	}

	private KeyStoreHolder(KeyStoreResource store) {
		synchronized (store.sync) {
			this.store = store;

			this.rcnt = store.rcnt;
		}
	}

	public boolean hasChanged() {
		synchronized (store.sync) {
			return this.rcnt != store.rcnt;
		}
	}

	@Override
	public Iterator<KeyStoreEntry> iterator() {
		synchronized (store.sync) {
			this.rcnt = store.rcnt;

			return store.iterator();
		}
	}

	@Override
	public KeyStoreEntry getByAlias(String alias) {
		return store.getByAlias(alias);
	}

	@Override
	public KeyStoreEntry getByEncodedCertificate(byte[] certificate) {
		return store.getByEncodedCertificate(certificate);
	}

	@Override
	public List<KeyStoreEntry> getByDN(String dn) {
		return store.getByDN(dn);
	}

	@Override
	public KeyStoreEntry getByJWK(JWK jwk) {
		return store.getByJWK(jwk);
	}

	@Override
	public KeyStoreEntry getByPublicKey(PublicKey key) {
		return store.getByPublicKey(key);
	}

	@Override
	public KeyStoreEntry getByX5T(String x5t) {
		return store.getByX5T(x5t);
	}

	@Override
	public KeyStoreEntry getByX5T256(String x5t256) {
		return store.getByX5T256(x5t256);
	}
}
