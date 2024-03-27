package com.vordel.circuit.filter.devkit.certmgr;

import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import com.nimbusds.jose.jwk.JWK;
import com.vordel.circuit.filter.devkit.context.resources.ResolvedResource;
import com.vordel.trace.Trace;

/**
 * base class for all keystore resources implementation. it provides a object to
 * synchronize on. Underlying implementation should override the
 * {@link #get(Object)} method for better performance. each method which access
 * underlying store must be synchronized on the relevant object. The
 * {@link #iterator()} must be done against an immutable view.
 * 
 * @author rdesaintleger@axway.com
 */
public abstract class KeyStoreResource extends AbstractMap<String, Certificate> implements Iterable<KeyStoreEntry>, ResolvedResource {
	/**
	 * Aliases Map view of certificates
	 */
	private final KeyStoreEntrySet entries = new KeyStoreEntrySet();

	protected final Object sync = new Object();

	/**
	 * reload count for this resource
	 */
	protected int rcnt = 0;

	/**
	 * Retrieve a {@link KeyStoreEntry} using its alias
	 * 
	 * @param alias alias of {@link KeyStoreEntry} to be retrieved
	 * @return the requested entry or <code>null</code> if no entry exists with this
	 *         alias
	 */
	public abstract KeyStoreEntry getByAlias(String alias);

	public final KeyStoreEntry getByCertificate(Certificate certificate) {
		if (certificate != null) {
			synchronized (sync) {
				try {
					byte[] encoded = certificate.getEncoded();

					return getByEncodedCertificate(encoded);
				} catch (CertificateEncodingException e) {
					Trace.error("unable to encode certificate", e);
				}
			}
		}

		return null;
	}

	public abstract KeyStoreEntry getByEncodedCertificate(byte[] certificate);

	public abstract KeyStoreEntry getByPublicKey(PublicKey key);

	public abstract List<KeyStoreEntry> getByDN(String dn);

	public abstract KeyStoreEntry getByX5T(String x5t);

	public abstract KeyStoreEntry getByX5T256(String x5t256);

	public abstract KeyStoreEntry getByJWK(JWK jwk);

	@Override
	public final Certificate put(String key, Certificate value) {
		/* store map view is immutable */
		throw new UnsupportedOperationException();
	}

	@Override
	public final Set<Entry<String, Certificate>> entrySet() {
		return entries;
	}

	private final class KeyStoreEntrySet extends AbstractSet<Entry<String, Certificate>> {
		@Override
		public int size() {
			Iterator<Entry<String, Certificate>> iterator = iterator();
			int size = 0;

			while ((size < Integer.MAX_VALUE) && iterator.hasNext()) {
				size++;
			}

			return size;
		}

		@Override
		public Iterator<Entry<String, Certificate>> iterator() {
			return new KeyStoreEntryIterator();
		}
	}

	private final class KeyStoreEntryIterator implements Iterator<Entry<String, Certificate>> {
		private final Iterator<KeyStoreEntry> entries = iterator();

		private Set<String> seen = new HashSet<String>();
		private KeyStoreEntry next = null;

		@Override
		public boolean hasNext() {
			while ((next == null) && (entries.hasNext())) {
				KeyStoreEntry item = entries.next();
				String key = item.getKey();

				/*
				 * ensure that we have an alias for this entry and the alias has not been
				 * already seen
				 */
				if ((key != null) && (!seen.add(key))) {
					next = item;
				}
			}

			return next != null;
		}

		@Override
		public Entry<String, Certificate> next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			try {
				return next;
			} finally {
				next = null;
			}
		}
	}
}
