package com.vordel.circuit.filter.devkit.certmgr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import com.vordel.security.cert.PersonalInfo;
import com.vordel.security.pem.PEM;
import com.vordel.store.cert.CertStore;
import com.vordel.trace.Trace;

public final class VordelKeyStore extends KeyStoreMapper {
	private static final VordelKeyStore INSTANCE;

	static {
		INSTANCE = new VordelKeyStore();
	}

	/**
	 * dirty flag. set when importing certificates into vordel cert store.
	 */
	private boolean dirty = true;

	private VordelKeyStore() {
		reload(true);
	}

	public static VordelKeyStore getInstance() {
		return INSTANCE;
	}

	public boolean reload(boolean force) {
		/*
		 * make a copy of references so only one lock is used at a time. This will avoid
		 * deadlocks.
		 */
		Map<String, PersonalInfo> infos = new HashMap<String, PersonalInfo>();
		boolean copied = false;
		boolean loaded = false;

		if (!force) {
			/* if reload is not forced, check the dirty flag. */
			synchronized (sync) {
				/*
				 * retrieve the current state of this keystore. dirty means data need to be
				 * refreshed.
				 */
				force |= dirty;
			}
		}

		if (force) {
			/* retrieve CertStore instance and check if available */
			CertStore store = CertStore.getInstance();

			if (store != null) {
				/*
				 * copy references to all infos in vordel certstore. assume that PersonalInfo
				 * object do not need to be protected by the lock.
				 */
				Map<String, PersonalInfo> aliases = store.getAliasToInfo();

				for (Entry<String, PersonalInfo> entry : aliases.entrySet()) {
					infos.put(entry.getKey(), entry.getValue());
				}

				/* assume data has been loaded in the context of a reload operation */
				copied = true;
			}
		}

		if (copied) {
			synchronized (sync) {
				/*
				 * now that we do have a safe copy of references lock this store for importing
				 * it
				 */
				Iterator<Entry<String, PersonalInfo>> iterator = infos.entrySet().iterator();

				reload(iterator, (entry) -> {
					String alias = entry.getKey();
					PersonalInfo info = entry.getValue();

					return KeyStoreEntry.fromPersonalInfo(alias, info);
				});

				/* the store is now loaded with fresh information */
				loaded = true;
				dirty = false;
			}
		}

		return loaded;
	}

	/**
	 * entry point for importing certificates info the vordel keystore. Any service
	 * using the {@link VordelKeyStore} wrapper will be updated transparently. This
	 * method can be used safely during deployment. Beware of imports during message
	 * handling since the base vordel certificate store does not provide critical
	 * sections. Loading certificates under heavy load may produce inconsistencies
	 * in the vordel keystore. importing certificates during filter attachment is
	 * totally safe.
	 * 
	 * @param entries        iterable of entries to be imported
	 * @param aliasGenerator a function which generate the alias to be used with
	 *                       existing PersonalInfo object and {@link KeyStoreEntry}
	 *                       to be imported.
	 * @param useThread      use separate thread to avoid disposeof Vordel JCE
	 *                       Objects. use 'false' when in script attachment phase,
	 *                       'true' if handling a message.
	 * @return true if any certificate has been successfully imported.
	 */
	public boolean importEntries(Iterable<KeyStoreEntry> entries, BiFunction<PersonalInfo, KeyStoreEntry, String> aliasGenerator, boolean useThread) {
		Map<String, KeyStoreEntry> aliases = new HashMap<String, KeyStoreEntry>();
		List<PersonalInfo> imported = new ArrayList<PersonalInfo>();
		CertStore store = CertStore.getInstance();

		if (store != null) {
			/* start by filtering X509 Certificates */
			for (KeyStoreEntry entry : entries) {
				if (entry.isX509Certificate()) {
					X509Certificate certificate = (X509Certificate) entry.getCertificate();
					X500Principal subject = certificate.getSubjectX500Principal();
					PersonalInfo info = null;

					try {
						String thumb = CertStore.getCertThumbprint(certificate);

						info = store.getPersonalInfoByThumbprint(thumb);
					} catch (NoSuchAlgorithmException | CertificateEncodingException e) {
						Trace.error(String.format("unable to check for personal infos for '%s'", subject.toString()), e);
					}

					String alias = aliasGenerator.apply(info, entry);

					if (alias != null) {
						aliases.put(alias, entry);
					}
				}
			}

			Runnable installer = new X509CertificateInstaller(store, aliases, imported);

			if (useThread) {
				Thread task = new Thread(installer);

				try {
					task.run();
					task.join();
				} catch (InterruptedException e) {
					Trace.error("interrupted while importing certificates", e);
				} finally {
				}
			} else {
				installer.run();
			}
		}

		return !imported.isEmpty();
	}

	/**
	 * convert any private key object to a vordel representation of the private key
	 * 
	 * @param privateKey the private key to be converted
	 * @return the converted private key using vorder runtime
	 */
	public static PrivateKey toVordelPrivateKey(PrivateKey privateKey) {
		if (privateKey != null) {
			try {
				StringWriter pem = new StringWriter();
				JcaPEMWriter writer = new JcaPEMWriter(pem);

				try {
					writer.writeObject(privateKey);
				} finally {
					writer.close();
				}

				InputStream in = new ByteArrayInputStream(pem.toString().getBytes(StandardCharsets.UTF_8));

				try {
					PEM reader = new PEM(in, null);

					Iterator<PrivateKey> keys = reader.getPrivateKeys().iterator();

					if (keys.hasNext()) {
						/* return the first converted private key */
						return keys.next();
					}
				} finally {
					in.close();
				}
			} catch (IOException | InvalidKeySpecException e) {
				Trace.error("unable to convert private key", e);
			}
		}

		return null;
	}

	private static class X509CertificateInstaller implements Runnable {
		private final Map<String, KeyStoreEntry> aliases;
		private final List<PersonalInfo> imported;
		private final CertStore store;

		private X509CertificateInstaller(CertStore store, Map<String, KeyStoreEntry> aliases, List<PersonalInfo> imported) {
			this.store = store;
			this.aliases = aliases;
			this.imported = imported;
		}

		@Override
		public void run() {
			Iterator<Entry<String, KeyStoreEntry>> iterator = aliases.entrySet().iterator();
			boolean modified = false;

			/*
			 * the first step consist of importing certificates and private keys in the
			 * vordel expected format. Any exception will cancel the import for a single
			 * entry
			 */

			while (iterator.hasNext()) {
				Entry<String, KeyStoreEntry> element = iterator.next();
				KeyStoreEntry entry = element.getValue();
				String alias = element.getKey();

				X500Principal subject = ((X509Certificate) entry.getCertificate()).getSubjectX500Principal();

				try {
					/* retrieve API Gateway certificate and private key representations */
					byte[] encoded = entry.getCertificate().getEncoded();
					X509Certificate certificate = CertStore.decodeCert(encoded);
					PrivateKey privateKey = toVordelPrivateKey(entry.getPrivateKey());

					imported.add(store.addEntry(certificate, privateKey, alias));

					modified |= true;
				} catch (NoSuchAlgorithmException | CertificateEncodingException e) {
					Trace.error(String.format("JCE exception importing certificate '%s' using alias '%s'", alias, subject.toString()), e);
				} catch (Exception e) {
					Trace.error(String.format("unexpected exception importing certificate '%s' using alias '%s'", alias, subject.toString()), e);
				}
			}

			setCertificateChains();

			if (modified) {
				synchronized (INSTANCE.sync) {
					INSTANCE.dirty |= true;
				}
			}
		}

		private void setCertificateChains() {
			/*
			 * Each imported entry has not the certificate chain set. Update it using the
			 * KeyStorePathBuilder.
			 */

			/* create objects for holding anchors */
			Map<X500Principal, TrustAnchor> anchors = new HashMap<X500Principal, TrustAnchor>();
			List<Certificate> authorities = new ArrayList<Certificate>();

			/* retrieve anchors */
			KeyStorePathBuilder.setupAnchors(new X509CertificateIterator(store.getAliasToInfo().values().iterator()), anchors, null, authorities);

			for (PersonalInfo info : imported) {
				X509Certificate certificate = info.certificate;
				List<Certificate> path = new ArrayList<Certificate>();
				List<X509Certificate> x509 = new ArrayList<X509Certificate>();

				/* build cert path and fill chain according to vordel cert store content */
				KeyStorePathBuilder.getCertPath(certificate, path, anchors, authorities);

				for (Certificate item : path) {
					if (item instanceof X509Certificate) {
						/* just ensure that there won't be any class cast exception */
						x509.add((X509Certificate) item);
					}
				}

				/* set certificate chain */
				info.chain = x509.toArray(new X509Certificate[0]);
			}
		}
	}

	private static class X509CertificateIterator implements Iterator<X509Certificate> {
		private final Iterator<PersonalInfo> iterator;

		private X509Certificate next = null;

		public X509CertificateIterator(Iterator<PersonalInfo> iterator) {
			this.iterator = iterator;
		}

		@Override
		public boolean hasNext() {
			while ((next == null) && iterator.hasNext()) {
				PersonalInfo info = iterator.next();

				if (info != null) {
					next = info.certificate;
				}
			}

			return next != null;
		}

		@Override
		public X509Certificate next() {
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
