package com.vordel.circuit.filter.devkit.certmgr;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertPath;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathBuilderResult;
import java.security.cert.CertSelector;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.filter.devkit.context.resources.CacheResource;
import com.vordel.trace.Trace;

public class KeyStorePathBuilder {
	private static final Base64.Encoder KEY_ENCODER = Base64.getUrlEncoder();

	private static final BouncyCastleProvider PROVIDER = new BouncyCastleProvider();

	private final List<KeyStoreHolder> trusted = new ArrayList<KeyStoreHolder>();
	private final CacheResource cache;

	private final List<X509Certificate> authorities = new ArrayList<X509Certificate>();
	private final Map<X500Principal, TrustAnchor> anchors = new HashMap<X500Principal, TrustAnchor>();

	public KeyStorePathBuilder(KeyStoreResource... trust) {
		this(null, trust);
	}

	public KeyStorePathBuilder(CacheResource cache, KeyStoreResource... trust) {
		this.cache = cache;

		for (KeyStoreResource store : trust) {
			if (store != null) {
				trusted.add(KeyStoreHolder.fromKeyStoreResource(store));
			}
		}

		reload(true);
	}

	/**
	 * reload certification authorities from underlying trusted stores. This method
	 * will not flush certification path cache.
	 * 
	 * @param force if false, reload only if underlying store has changed
	 * @return 'true' if trust stores has been reloaded. false otherwise.
	 */
	public boolean reload(boolean force) {
		synchronized (trusted) {
			if (!force) {
				for (KeyStoreHolder holder : trusted) {
					force |= holder.hasChanged();
				}
			}

			if (force) {
				List<X509Certificate> certificates = new ArrayList<X509Certificate>();

				for (KeyStoreHolder holder : trusted) {
					Iterator<KeyStoreEntry> iterator = holder.iterator();

					while (iterator.hasNext()) {
						KeyStoreEntry entry = iterator.next();

						if (entry.isX509Certificate()) {
							Certificate certificate = entry.getCertificate();

							if (certificate instanceof X509Certificate) {
								certificates.add((X509Certificate) certificate);
							}
						}
					}
				}

				setupAnchors(certificates.iterator(), anchors, authorities, null);
			}
		}

		return force;
	}

	/**
	 * from a given iterator of trusted certificates, fill anchor map and
	 * intermediate autorities list. a third list is filled with all values from
	 * iterator (except duplicates). This method does not check for certificates
	 * validity.
	 * 
	 * @param iterator      iterator of X509 certificates
	 * @param anchors       list of anchors (self signed certificates or
	 *                      intermediates without root CA)
	 * @param intermediates intermediate authorities for which we do have the root
	 *                      CA
	 * @param authorities   list of all trusted certificates without duplicates.
	 */
	public static void setupAnchors(Iterator<X509Certificate> iterator, Map<X500Principal, TrustAnchor> anchors, List<? super X509Certificate> intermediates, List<? super X509Certificate> authorities) {
		Map<X500Principal, X509Certificate> trusts = new HashMap<X500Principal, X509Certificate>();
		Set<ByteBuffer> seen = new HashSet<ByteBuffer>();

		if (intermediates != null) {
			intermediates.clear();
		}

		if (authorities != null) {
			authorities.clear();
		}

		anchors.clear();

		/* make a first path to retrieve all certifications authorities */
		while (iterator.hasNext()) {
			X509Certificate certificate = iterator.next();

			try {
				ByteBuffer key = ByteBuffer.wrap(certificate.getEncoded());

				if (seen.add(key)) {
					X500Principal subject = certificate.getSubjectX500Principal();

					trusts.put(subject, certificate);

					if (authorities != null) {
						authorities.add(certificate);
					}
				}
			} catch (CertificateEncodingException e) {
				Trace.error("got error encoding certificate to binary form", e);
			}
		}

		/* now, set root anchors to a separate Map */
		for (Entry<X500Principal, X509Certificate> entry : trusts.entrySet()) {
			X509Certificate certificate = entry.getValue();
			X500Principal subject = certificate.getSubjectX500Principal();
			X500Principal issuer = certificate.getIssuerX500Principal();

			if (subject.equals(issuer) || (trusts.get(issuer) == null)) {
				/* either this is a root CA, or we do not have root certificate */
				TrustAnchor anchor = new TrustAnchor(certificate, null);

				/* assume that we do not have multiple anchors with the same name */
				anchors.putIfAbsent(subject, anchor);
			} else if (intermediates != null) {
				/* this is an intermediate authority */
				intermediates.add(certificate);
			}
		}
	}

	public final KeyStoreEntry getTrustEntryByCertificate(Certificate certificate) {
		if (certificate != null) {
			synchronized (trusted) {
				try {
					byte[] encoded = certificate.getEncoded();

					return getTrustEntryByEncodedCertificate(encoded);
				} catch (CertificateEncodingException e) {
					Trace.error("unable to encode certificate", e);
				}
			}
		}

		return null;
	}

	public final KeyStoreEntry getTrustEntryByEncodedCertificate(byte[] encoded) {
		if (encoded != null) {
			synchronized (trusted) {
				for (KeyStoreHolder holder : trusted) {
					KeyStoreEntry entry = holder.getByEncodedCertificate(encoded);

					if (entry != null) {
						return entry;
					}
				}
			}
		}

		return null;
	}

	private static CertPathHolder getCertPathHolder(X509Certificate certificate, Set<TrustAnchor> anchors, Collection<? extends Certificate> authorities, List<? extends Certificate> untrusted) {
		String name = certificate.getSubjectX500Principal().toString();
		PublicKey key = certificate.getPublicKey();
		CertPathHolder holder = null;

		try {
			/* use bouncy castle for this. Slower but stronger */
			CertPathBuilder builder = CertPathBuilder.getInstance("PKIX", PROVIDER);
			PKIXBuilderParameters parameters = new PKIXBuilderParameters(anchors, getPublicKeySelector(key));

			parameters.setRevocationEnabled(false);
			parameters.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(authorities)));

			if (untrusted != null) {
				parameters.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(untrusted)));
			}

			parameters.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(Collections.singleton(certificate))));

			CertPathBuilderResult result = builder.build(parameters);

			/* retrieve PKIX results and trust anchor */
			CertPath pkix = result.getCertPath();
			TrustAnchor anchor = getTrustAnchor(certificate, pkix.getCertificates(), anchors);

			/* create the holder object */
			holder = new CertPathHolder(pkix, anchor);
		} catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
			Trace.error(String.format("unable to build CertPath object for '%s'", name), e);
		} catch (CertPathBuilderException e) {
			if (Trace.isDebugEnabled()) {
				Trace.info(String.format("certification path could not be validated for '%s'", name), e);
			} else {
				Trace.info(String.format("certification path could not be validated for '%s'", name));
			}
		} catch (RuntimeException e) {
			Trace.error(String.format("unexpected exception when building certpath for '%s'", name), e);
		}

		return holder;
	}

	private final CertPathHolder getCertPathHolder(X509Certificate certificate, List<? extends Certificate> untrusted) {
		Collection<X509Certificate> authorities = null;
		Set<TrustAnchor> anchors = null;

		synchronized (trusted) {
			/* duplicate anchors and authorities so we can work outside critical section */
			anchors = new HashSet<TrustAnchor>(this.anchors.values());
			authorities = new ArrayList<X509Certificate>(this.authorities);
		}

		return getCertPathHolder(certificate, anchors, authorities, untrusted);
	}

	private static final TrustAnchor getTrustAnchor(X509Certificate certificate, List<? extends Certificate> path, Set<TrustAnchor> anchors) {
		Certificate last = null;

		if (path.size() > 0) {
			last = path.get(path.size() - 1);
		} else {
			X500Principal subject = certificate.getSubjectX500Principal();
			X500Principal issuer = certificate.getIssuerX500Principal();

			if (subject.equals(issuer)) {
				/* certificate is self signed and trusted */
				last = certificate;
			}
		}

		if (last instanceof X509Certificate) {
			X500Principal issuer = ((X509Certificate) last).getIssuerX500Principal();

			for (TrustAnchor anchor : anchors) {
				X509Certificate root = anchor.getTrustedCert();

				if (issuer.equals(root.getSubjectX500Principal())) {
					return anchor;
				}
			}
		}

		return null;
	}

	public final CertPath getCertPathWithCache(X509Certificate certificate, List<? super X509Certificate> path) throws CircuitAbortException {
		PublicKey key = certificate.getPublicKey();
		CertPathHolder holder = null;
		String cacheKey = null;

		if (cache != null) {
			cacheKey = KEY_ENCODER.encodeToString(key.getEncoded());
			holder = (CertPathHolder) cache.getCachedValue(cacheKey);

			if (holder == null) {
				holder = getCertPathHolder(certificate, null);
				cache.putCachedValue(cacheKey, holder);
			}
		} else {
			holder = getCertPathHolder(certificate, null);
		}

		path.clear();

		if ((holder != null) && (holder.path != null)) {
			/* refine the path from pkix result */
			path.addAll(holder.path);

			return holder.pkix;
		} else {
			path.add(certificate);
		}

		return null;
	}

	public final CertPath getCertPath(X509Certificate certificate, List<? super X509Certificate> path, List<? extends Certificate> untrusted) {
		CertPathHolder holder = getCertPathHolder(certificate, untrusted);

		path.clear();

		if ((holder != null) && (holder.path != null)) {
			/* refine the path from pkix result */
			path.addAll(holder.path);

			return holder.pkix;
		} else {
			path.add(certificate);
		}

		return null;
	}

	public static CertPath getCertPath(X509Certificate certificate, List<? super X509Certificate> path, Map<X500Principal, TrustAnchor> anchors, List<? extends Certificate> authorities) {
		Set<TrustAnchor> roots = new HashSet<TrustAnchor>(anchors.values());
		CertPathHolder holder = getCertPathHolder(certificate, roots, authorities, null);

		path.clear();

		if ((holder != null) && (holder.path != null)) {
			/* refine the path from pkix result */
			path.addAll(holder.path);

			return holder.pkix;
		} else {
			path.add(certificate);
		}

		return null;
	}

	public static final CertSelector getPublicKeySelector(PublicKey key) {
		X509CertSelector result = null;

		if (key != null) {
			result = new X509CertSelector();

			result.setSubjectPublicKey(key);
		}

		return result;
	}

	private static final class CertPathHolder implements Serializable {
		private static final long serialVersionUID = -7648994056458753481L;

		private final List<X509Certificate> path;
		private final CertPath pkix;

		private CertPathHolder(CertPath pkix, TrustAnchor anchor) {
			List<X509Certificate> path = null;

			if (pkix != null) {
				Iterator<? extends Certificate> iterator = pkix.getCertificates().iterator();
				Set<ByteBuffer> seen = new HashSet<ByteBuffer>();

				path = new ArrayList<X509Certificate>();

				while (iterator.hasNext()) {
					Certificate certificate = iterator.next();

					if (certificate instanceof X509Certificate) {
						PublicKey key = certificate.getPublicKey();
						byte[] encoded = key.getEncoded();

						if (seen.add(ByteBuffer.wrap(encoded))) {
							path.add((X509Certificate) certificate);
						}
					}
				}

				if (anchor != null) {
					X509Certificate trusted = anchor.getTrustedCert();

					if (trusted != null) {
						PublicKey key = trusted.getPublicKey();
						byte[] encoded = key.getEncoded();

						if (seen.add(ByteBuffer.wrap(encoded))) {
							path.add(trusted);
						}
					}
				}
			}

			this.path = path;
			this.pkix = pkix;
		}
	}
}
