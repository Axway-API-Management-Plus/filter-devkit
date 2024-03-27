package com.vordel.circuit.filter.devkit.certmgr;

import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.security.auth.x500.X500Principal;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.util.Base64URL;
import com.vordel.trace.Trace;

public abstract class KeyStoreMapper extends KeyStoreResource {
	private final List<KeyStoreEntry> entryList = new ArrayList<KeyStoreEntry>();

	private final Map<X500Principal, List<KeyStoreEntry>> subjects = new HashMap<X500Principal, List<KeyStoreEntry>>();
	private final Map<ByteBuffer, KeyStoreEntry> certificates = new HashMap<ByteBuffer, KeyStoreEntry>();
	private final Map<ByteBuffer, KeyStoreEntry> publicKeys = new HashMap<ByteBuffer, KeyStoreEntry>();
	private final Map<String, KeyStoreEntry> aliases = new HashMap<String, KeyStoreEntry>();

	private final Map<String, KeyStoreEntry> x5t256s = new HashMap<String, KeyStoreEntry>();
	private final Map<String, KeyStoreEntry> x5ts = new HashMap<String, KeyStoreEntry>();

	protected final <T> void reload(Iterator<T> entries, Function<T, KeyStoreEntry> parser) {
		List<KeyStoreEntry> parsed = new ArrayList<KeyStoreEntry>();

		Map<ByteBuffer, Certificate> certificates = new HashMap<ByteBuffer, Certificate>();
		Map<ByteBuffer, PrivateKey> privateKeys = new HashMap<ByteBuffer, PrivateKey>();

		/*
		 * start by parsing all entries. This can take time, so do it on a local list.
		 * Also, keep track of private keys for refining entries if needed.
		 */
		while (entries.hasNext()) {
			KeyStoreEntry entry = parser.apply(entries.next());

			if (entry != null) {
				Certificate certificate = entry.getCertificate();
				PrivateKey privateKey = entry.getPrivateKey();
				PublicKey pubkey = entry.getPublicKey();

				if ((pubkey != null) && ((certificate != null) || (privateKey != null))) {
					byte[] encoded = pubkey.getEncoded();
					ByteBuffer key = ByteBuffer.wrap(encoded);

					if (certificate != null) {
						certificates.putIfAbsent(key, certificate);
					}

					if (privateKey != null) {
						privateKeys.putIfAbsent(key, privateKey);
					}
				}

				parsed.add(entry);
			}
		}

		/* reload internal maps */
		reload(parsed, certificates, privateKeys);
	}

	private final void reload(List<KeyStoreEntry> entries, Map<ByteBuffer, Certificate> refined, Map<ByteBuffer, PrivateKey> privateKeys) {
		synchronized (sync) {
			ListIterator<KeyStoreEntry> iterator = entries.listIterator();

			entryList.clear();
			aliases.clear();
			x5t256s.clear();
			x5ts.clear();

			certificates.clear();
			publicKeys.clear();

			while (iterator.hasNext()) {
				KeyStoreEntry entry = iterator.next();
				Certificate certificate = entry.getCertificate();
				PrivateKey privateKey = entry.getPrivateKey();
				PublicKey pubkey = entry.getPublicKey();

				if (pubkey != null) {
					byte[] encoded = pubkey.getEncoded();
					ByteBuffer key = ByteBuffer.wrap(encoded);

					if (certificate == null) {
						certificate = refined.get(key);
					}

					if (privateKey == null) {
						privateKey = privateKeys.get(key);

						if (privateKey != null) {
							entry = entry.refineCertificate(certificate, privateKey);

							/* update the entry in the list */
							iterator.set(entry);
						}

						publicKeys.putIfAbsent(key, entry);
					}
				}

				String x5t = entry.getX5T();
				String x5t256 = entry.getX5T();
				String alias = entry.getAlias();

				if (alias != null) {
					aliases.putIfAbsent(alias, entry);
				}

				if (x5t != null) {
					x5ts.putIfAbsent(x5t, entry);
				}

				if (x5t256 != null) {
					x5t256s.putIfAbsent(x5t256, entry);
				}

				if (certificate != null) {
					try {
						byte[] encoded = certificate.getEncoded();
						ByteBuffer key = ByteBuffer.wrap(encoded);

						certificates.putIfAbsent(key, entry);
					} catch (CertificateEncodingException e) {
						Trace.error("unable to encode certificate", e);
					}
				}

				if (certificate instanceof X509Certificate) {
					X500Principal subject = ((X509Certificate) certificate).getSubjectX500Principal();
					List<KeyStoreEntry> named = subjects.computeIfAbsent(subject, (key) -> new ArrayList<KeyStoreEntry>());

					named.add(entry);
				}
			}

			/* sort named certificates according to highest expiration date */
			for (Entry<X500Principal, List<KeyStoreEntry>> entry : subjects.entrySet()) {
				List<KeyStoreEntry> named = entry.getValue();

				Collections.sort(named, (entry1, entry2) -> {
					/* since we only put X509Certificate, casting is safe here */
					X509Certificate cert1 = (X509Certificate) entry1.getCertificate();
					X509Certificate cert2 = (X509Certificate) entry2.getCertificate();
					long stamp1 = cert1.getNotAfter().getTime();
					long stamp2 = cert2.getNotAfter().getTime();

					return -Long.compare(stamp1, stamp2);
				});

				/* last step for identities : remove duplicates */
				Iterator<KeyStoreEntry> sorted = named.iterator();
				Set<Date> seen = new HashSet<Date>();

				while (sorted.hasNext()) {
					/*
					 * assume that two certificates with same subject and same expiration date are
					 * the same
					 */
					X509Certificate certificate = (X509Certificate) sorted.next().getCertificate();
					Date expire = certificate.getNotAfter();

					if (!seen.add(expire)) {
						sorted.remove();
					}
				}

				/* collection sorted, make list immutable */
				entry.setValue(Collections.unmodifiableList(named));
			}

			entryList.addAll(entries);

			/* increment reload count */
			rcnt++;
		}
	}

	@Override
	public final Iterator<KeyStoreEntry> iterator() {
		synchronized (sync) {
			/* make copy of object references, so a reload will not change the iterator */
			return Collections.unmodifiableList(new ArrayList<KeyStoreEntry>(entryList)).iterator();
		}
	}

	@Override
	public final Certificate get(Object key) {
		synchronized (sync) {
			KeyStoreEntry entry = aliases.get(key);

			return entry == null ? null : entry.getCertificate();
		}
	}

	@Override
	public final KeyStoreEntry getByAlias(String alias) {
		synchronized (sync) {
			return aliases.get(alias);
		}
	}

	@Override
	public final KeyStoreEntry getByEncodedCertificate(byte[] encoded) {
		if (encoded != null) {
			synchronized (sync) {
				return certificates.get(ByteBuffer.wrap(encoded));
			}
		}

		return null;
	}

	@Override
	public final KeyStoreEntry getByPublicKey(PublicKey key) {
		if (key != null) {
			synchronized (sync) {
				byte[] encoded = key.getEncoded();

				publicKeys.get(ByteBuffer.wrap(encoded));
			}
		}

		return null;
	}

	public final List<KeyStoreEntry> getByDN(String dn) {
		List<KeyStoreEntry> named = Collections.emptyList();

		if (dn != null) {
			X500Principal subject = new X500Principal(dn);

			named = subjects.getOrDefault(subject, named);
		}

		return named;
	}

	@Override
	public final KeyStoreEntry getByX5T(String x5t) {
		synchronized (sync) {
			return x5ts.get(x5t);
		}
	}

	@Override
	public final KeyStoreEntry getByX5T256(String x5t256) {
		synchronized (sync) {
			return x5t256s.get(x5t256);
		}
	}

	@Override
	public final KeyStoreEntry getByJWK(JWK jwk) {
		if (jwk != null) {
			synchronized (sync) {
				Base64URL x5t256 = jwk.getX509CertSHA256Thumbprint();

				if (x5t256 != null) {
					return x5t256s.get(x5t256.toString());
				}

				Base64URL x5t = jwk.getX509CertSHA256Thumbprint();

				if (x5t != null) {
					return x5t256s.get(x5t.toString());
				}

				String alias = jwk.getKeyID();

				if (alias != null) {
					return aliases.get(alias);
				}
			}
		}

		return null;
	}
}
