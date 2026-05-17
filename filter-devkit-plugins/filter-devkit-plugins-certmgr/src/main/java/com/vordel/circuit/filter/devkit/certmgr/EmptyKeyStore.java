package com.vordel.circuit.filter.devkit.certmgr;

import java.security.PublicKey;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.nimbusds.jose.jwk.JWK;

public final class EmptyKeyStore extends KeyStoreResource {
	private static final Set<KeyStoreEntry> EMPTY = Collections.emptySet();
	
	public static final EmptyKeyStore INSTANCE = new EmptyKeyStore();
	
	private EmptyKeyStore() {
	}
	
	@Override
	public Iterator<KeyStoreEntry> iterator() {
		return EMPTY.iterator();
	}

	@Override
	public KeyStoreEntry getByAlias(String alias) {
		return null;
	}

	@Override
	public KeyStoreEntry getByEncodedCertificate(byte[] certificate) {
		return null;
	}

	@Override
	public KeyStoreEntry getByPublicKey(PublicKey key) {
		return null;
	}

	@Override
	public List<KeyStoreEntry> getByDN(String dn) {
		return Collections.emptyList();
	}

	@Override
	public KeyStoreEntry getByX5T(String x5t) {
		return null;
	}

	@Override
	public KeyStoreEntry getByX5T256(String x5t256) {
		return null;
	}

	@Override
	public KeyStoreEntry getByJWK(JWK jwk) {
		return null;
	}
}
