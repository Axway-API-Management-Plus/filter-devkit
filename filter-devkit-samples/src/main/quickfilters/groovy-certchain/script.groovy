package com.vordel.sdk.samples

import java.nio.charset.Charset
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.X509Certificate

import javax.security.auth.x500.X500Principal

import com.vordel.circuit.CircuitAbortException
import com.vordel.circuit.Message
import com.vordel.config.Circuit
import com.vordel.config.ConfigContext
import com.vordel.el.Selector
import com.vordel.es.Entity
import com.vordel.es.EntityStoreException
import com.vordel.security.cert.PersonalInfo
import com.vordel.store.cert.CertStore
import com.vordel.trace.Trace

import groovy.transform.Field

@Field private Map<X500Principal, List<X509Certificate>> imported;
@Field private Selector<Object> certificateSelector;
@Field private byte[] password;
@Field private String caOutput;

public void attach(ConfigContext ctx, Entity entity) {
	String name = entity.getStringValue("name");

	Trace.info(String.format("attaching filter '%s' external Certificate Chain check", name));

	byte[] password = null;
	int passwordType = entity.getIntegerValue("passwordType");

	switch(passwordType) {
		case 1: /* extract encrypted value from configuration */
			password = ctx.getCipher().decrypt(entity.getEncryptedValue("password"));
			break;
		case 2: /* extract value from selector */
			password = asByteArray(new Selector<Object>(entity.getStringValue("passwordSelector"), Object.class));
			break;
		case 0: /* handle as no password */
		default:
			break;
	}

	String location = new Selector<String>(entity.getStringValue("location"), String.class).substitute(com.vordel.common.Dictionary.empty);

	caOutput = entity.getStringValue("caOutput")
	certificateSelector = new Selector<Object>(entity.getStringValue("certificateSelector"), Object.class);
	imported = importCAs(location, password.length == 0 ? null : password);
}

public void detach() {
	Trace.debug("filter detach");

	imported.clear();
}

/**
 * Check that the http request client certificate is valid (not before/not after) and
 * signed by one of the imported CA. this function will set the property 'http.request.clientcert.ca'
 * with the certificate authority which matched.
 * 
 * @param msg current message
 * @return 'true' if the certificate has been validated
 */
public boolean invoke(Circuit p, Message msg) throws CircuitAbortException {
	X509Certificate clientcert = retrieveCertificate(certificateSelector.substitute(msg));
	boolean checked = false;

	if (clientcert != null) {
		try {
			/* check client certificate dates */
			clientcert.checkValidity();

			/* retrieve issuer name and associated certificates */
			X500Principal issuerDN = clientcert.getIssuerX500Principal();
			List<X509Certificate> cas = imported.get(issuerDN);

			if (cas != null) {
				Iterator<X509Certificate> iterator = cas.iterator();

				while((!checked) && iterator.hasNext()) {
					X509Certificate ca = iterator.next();

					try {
						clientcert.verify(ca.getPublicKey());

						/* signal certificate is valid */
						checked |= true;

						/* save CA in message for future use */
						msg.put(caOutput, ca);
					} catch(Exception e) {
						/* ignore */
						Trace.error(e);
					}
				}

				if (!checked) {
					Trace.error("client certificate is not valid (not signed by a known CA)");
				}
			} else {
				Trace.error("unable to find CA for client certificate");
			}
		} catch(Exception e) {
			Trace.error("client certificate is not valid (wrong dates)");
		}
	} else {
		Trace.error("no certificate available");
	}

	return checked;
}

private static byte[] asByteArray(Selector<Object> selector) {
	Object value = selector.substitute(com.vordel.common.Dictionary.empty);
	byte[] password = null;

	if (value instanceof byte[]) {
		password = value;
	} else if (value instanceof CharSequence) {
		password = value.toString().getBytes("UTF-8");
	} else {
		throw new EntityStoreException("Password selector can only a byte array or a string");
	}

	return password;
}

private static X509Certificate retrieveCertificate(Object value) {
	X509Certificate result = null;

	if (value instanceof X509Certificate) {
		result = value;
	}

	return result;
}

private static Map<X500Principal, List<X509Certificate>> importCAs(String keyStorePath, byte[] password) {
	Map<X500Principal, List<X509Certificate>> imported = new HashMap<X500Principal, List<X509Certificate>>();
	CertStore store = CertStore.getInstance();

	/*
	 * in policy studio, store is null... check for this.
	 */
	if ((store != null) && (keyStorePath != null)) {
		File keyStoreFile = new File(keyStorePath);

		if (keyStoreFile.isFile()) {
			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			Comparator<X509Certificate> comparator = new ExpirationComparator();

			String p = new String(password, Charset.defaultCharset())
			keyStore.load(new FileInputStream(keyStoreFile), p.toCharArray());

			Enumeration<String> aliases = keyStore.aliases();

			while (aliases.hasMoreElements()) {
				String alias = aliases.nextElement();

				try {
					Certificate certificate = keyStore.getCertificate(alias);

					if (certificate instanceof X509Certificate) {
						/* decode certificate using internal factories */
						X509Certificate x509 = CertStore.decodeCert(certificate.getEncoded());
						boolean[] keyUsage = x509.getKeyUsage();
						/*
						 * check if the key usage extension is present or if the basic constraint CA
						 * extension is present.
						 */
						if (((keyUsage != null) && keyUsage[5]) || (x509.getBasicConstraints() != -1)) {
							X500Principal subjectDN = x509.getIssuerX500Principal();

							if (!imported.containsKey(subjectDN)) {
								PersonalInfo info = store.getPersonalInfoByCertificate(x509);

								if (info == null) {
									info = store.getPersonalInfoByAlias(alias);

									if ((info != null) && (!equals(x509, info.certificate))) {
										/* alias already exists... try DN */
										alias = subjectDN.getName(X500Principal.RFC2253);
										info = store.getPersonalInfoByAlias(alias);

										if ((info != null) && (!equals(x509, info.certificate))) {
											/* DN already exists, try thumbprint */
											alias = CertStore.getCertThumbprint(x509);
											info = store.getPersonalInfoByAlias(alias);

											if ((info != null) && (!equals(x509, info.certificate))) {
												/* no more ideas... trace error */
												alias = null;

												Trace.error(String.format("can't import '%s' (an alias already has the same name)", alias));
											}
										}
									}
								}

								if (alias != null) {
									if (info == null) {
										Trace.info(String.format("add certificate entry for '%s' with alias '%s'", subjectDN.getName(), alias));

										/* add entry to master store, this fill enable filter usage */
										info = store.addEntry(x509, null, alias);
									} else {
										Trace.info(String.format("use existing certificate entry for '%s'", subjectDN.getName()));
									}

									/* get existing certificate reference (free some memory) */
									x509 = info.certificate;
								}

								if (info != null) {
									List<X509Certificate> list = imported.get(subjectDN);

									if (list == null) {
										list = new ArrayList<X509Certificate>();

										imported.put(subjectDN, list);
									}

									if (!contains(list, x509)) {
										Trace.info(String.format("add certificate '%s' to trusted list", subjectDN.getName()));

										/* adds the new CA certificate into the list */
										list.add(x509);

										Trace.info(x509.toString());

										/* and keep list sorted */
										Collections.sort(list, comparator);
									}
								}
							}
						} else {
							Trace.info(String.format("alias '%s' is not a CA, skipping", alias));
						}
					}
				} catch (Exception e) {
					Trace.error(String.format("exception processing alias '%s', skipping", alias), e);
				}
			}
		} else {
			Trace.info(String.format("JKS File '%s' does not exist, skipping import", keyStoreFile));
		}
	}

	return imported;
}

private static boolean contains(List<X509Certificate> list, X509Certificate x509) {
	Iterator<X509Certificate> iterator = list.iterator();
	boolean contained = false;

	while((!contained) && iterator.hasNext()) {
		contained |= equals(iterator.next(), x509);
	}

	return contained;
}

private static boolean equals(Certificate certificate1, Certificate certificate2) {
	boolean equals = false;


	if (certificate1 == null) {
		equals = certificate2 == null;
	} else {
		byte[] encoded1 = certificate1.getEncoded();
		byte[] encoded2 = certificate2.getEncoded();

		equals = Arrays.equals(encoded1, encoded2);
	}

	return equals;
}

class ExpirationComparator implements Comparator<X509Certificate> {
	@Override
	public int compare(X509Certificate o1, X509Certificate o2) {
		Date date1 = o1.getNotAfter();
		Date date2 = o2.getNotAfter();

		return date1.compareTo(date2);
	}
}