package com.vordel.circuit.filter.devkit.certmgr;

import java.io.IOException;
import java.io.StringWriter;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import javax.crypto.SecretKey;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.AsymmetricJWK;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.SecretJWK;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.Base64URL;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.security.cert.PersonalInfo;
import com.vordel.trace.Trace;

public final class KeyStoreEntry implements Entry<String, Certificate> {
	private final String alias;
	private final Certificate certificate;
	private final PrivateKey privateKey;
	private final PublicKey publicKey;
	private final SecretKey secretKey;
	private final JWK privateJWK;
	private final JWK publicJWK;

	private final String x5t256;
	private final String x5t;
	
	public KeyStoreEntry(String alias, KeyStoreEntry entry) {
		this.alias = alias;
		this.certificate = entry.certificate;
		this.privateKey = entry.privateKey;
		this.publicKey = entry.publicKey;
		this.secretKey = entry.secretKey;
		this.privateJWK = entry.privateJWK;
		this.publicJWK = entry.publicJWK;
		this.x5t = entry.x5t;
		this.x5t256 = entry.x5t256;
	}
	
	public KeyStoreEntry(KeyStoreEntry entry, JWK transformed) {
		this.alias = entry.alias;
		this.certificate = entry.certificate;
		this.privateKey = entry.privateKey;
		this.publicKey = entry.publicKey;
		this.secretKey = entry.secretKey;
		this.privateJWK = transformed;
		this.publicJWK = transformed == null ? null : transformed.toPublicJWK();
		this.x5t = entry.x5t;
		this.x5t256 = entry.x5t256;
	}

	private KeyStoreEntry(KeyStoreEntry entry, Certificate certificate, PrivateKey privateKey) {
		PublicKey publicKey = entry.publicKey;

		if (certificate == null) {
			certificate = entry.certificate;
		} else {
			publicKey = certificate.getPublicKey();
		}

		JWK privateJWK = refineJWK(entry.alias, certificate, publicKey, privateKey);
		JWK publicJWK = null;

		Base64URL x5t = null;
		Base64URL x5t256 = null;

		if (privateJWK != null) {
			x5t = getX5T(privateJWK);
			x5t256 = privateJWK.getX509CertSHA256Thumbprint();
			publicJWK = privateJWK.toPublicJWK();
		}

		this.alias = entry.alias;
		this.certificate = certificate;
		this.privateKey = privateKey;
		this.publicKey = publicKey;
		this.secretKey = entry.secretKey;
		this.privateJWK = privateJWK;
		this.publicJWK = publicJWK;

		this.x5t = getX5Tx(certificate, x5t, "SHA-1");
		this.x5t256 = getX5Tx(certificate, x5t256, "SHA-256");
	}

	private KeyStoreEntry(String alias, Certificate certificate, PublicKey publicKey, PrivateKey privateKey) {
		JWK privateJWK = refineJWK(alias, certificate, publicKey, privateKey);
		JWK publicJWK = null;

		Base64URL x5t = null;
		Base64URL x5t256 = null;

		if (privateJWK != null) {
			x5t = getX5T(privateJWK);
			x5t256 = privateJWK.getX509CertSHA256Thumbprint();
			publicJWK = privateJWK.toPublicJWK();
		}

		this.alias = alias;
		this.certificate = certificate;
		this.privateKey = privateKey;
		this.publicKey = publicKey;
		this.secretKey = null;
		this.privateJWK = privateJWK;
		this.publicJWK = publicJWK;

		this.x5t = getX5Tx(certificate, x5t, "SHA-1");
		this.x5t256 = getX5Tx(certificate, x5t256, "SHA-256");
	}

	public KeyStoreEntry(KeyStore store, String alias, char[] password) {
		Certificate certificate = null;
		PrivateKey privateKey = null;
		PublicKey publicKey = null;

		SecretKey secretKey = null;

		JWK privateJWK = null;
		JWK publicJWK = null;

		Base64URL x5t = null;
		Base64URL x5t256 = null;

		try {
			certificate = store.getCertificate(alias);
			publicKey = certificate.getPublicKey();
		} catch (KeyStoreException e) {
			Trace.error(String.format("unable to read certificate using alias '%s'", alias), e);
		}

		try {
			privateJWK = JWK.load(store, alias, password);
			publicJWK = privateJWK.toPublicJWK();

			x5t = getX5T(privateJWK);
			x5t256 = privateJWK.getX509CertSHA256Thumbprint();
		} catch (KeyStoreException | JOSEException e) {
			Trace.error(String.format("unable to create JWK using alias '%s'", alias), e);
		}

		try {
			Key key = store.getKey(alias, password);

			if (key instanceof SecretKey) {
				secretKey = (SecretKey) key;
			} else if (key instanceof PrivateKey) {
				privateKey = (PrivateKey) key;
			}
		} catch (KeyStoreException | UnrecoverableKeyException | NoSuchAlgorithmException e) {
			Trace.error(String.format("unable to read secret/private key using alias '%s'", alias), e);
		}

		this.alias = alias;
		this.certificate = certificate;
		this.privateKey = privateKey;
		this.publicKey = publicKey;
		this.secretKey = secretKey;
		this.privateJWK = privateJWK;
		this.publicJWK = publicJWK;

		this.x5t = getX5Tx(certificate, x5t, "SHA-1");
		this.x5t256 = getX5Tx(certificate, x5t256, "SHA-256");
	}

	private KeyStoreEntry(JWK jwk) {
		PrivateKey privateKey = null;
		PublicKey publicKey = null;

		SecretKey secretKey = null;

		JWK publicJWK = null;

		Base64URL x5t = null;
		Base64URL x5t256 = null;

		try {
			if (jwk instanceof AsymmetricJWK) {
				publicKey = ((AsymmetricJWK) jwk).toPublicKey();
				privateKey = ((AsymmetricJWK) jwk).toPrivateKey();
			} else if (jwk instanceof SecretJWK) {
				secretKey = ((SecretJWK) jwk).toSecretKey();
			}

			publicJWK = jwk.toPublicJWK();
		} catch (JOSEException e) {
			Trace.error("unable to extract keys from JWK", e);
		}

		this.alias = jwk.getKeyID();
		this.certificate = null;
		this.privateKey = privateKey;
		this.publicKey = publicKey;
		this.secretKey = secretKey;
		this.privateJWK = jwk;
		this.publicJWK = publicJWK;

		this.x5t = getX5Tx(null, x5t, "SHA-1");
		this.x5t256 = getX5Tx(null, x5t256, "SHA-256");
	}

	public static KeyStoreEntry fromJWK(JWK jwk) {
		return jwk == null ? null : new KeyStoreEntry(jwk);
	}

	public static KeyStoreEntry fromKeyPair(PublicKey publicKey, PrivateKey privateKey) {
		return fromCertificate(null, null, publicKey, privateKey);
	}

	public static KeyStoreEntry fromPersonalInfo(String alias, PersonalInfo info) {
		return fromCertificate(alias, info.certificate, null, info.privateKey);
	}

	public static KeyStoreEntry fromCertificate(Certificate certificate, PrivateKey privateKey) {
		return fromCertificate(null, certificate, null, privateKey);
	}

	private static KeyStoreEntry fromCertificate(String alias, Certificate certificate, PublicKey publicKey, PrivateKey privateKey) {
		if ((publicKey == null) && (certificate != null)) {
			publicKey = certificate.getPublicKey();
		}

		return publicKey == null ? null : new KeyStoreEntry(alias, certificate, publicKey, privateKey);
	}

	private static String getX5Tx(Certificate certificate, Base64URL available, String hash) {
		if (available != null) {
			return available.toString();
		} else {
			Base64URL thumbprint = getX5Tx(certificate, hash);

			if (thumbprint != null) {
				return thumbprint.toString();
			}
		}

		return null;
	}

	private static Base64URL getX5Tx(Certificate certificate, String hash) {
		if ((certificate != null) && (hash != null)) {
			try {
				MessageDigest digest = MessageDigest.getInstance(hash);
				byte[] encoded = certificate.getEncoded();
				byte[] thumbprint = digest.digest(encoded);

				return Base64URL.encode(thumbprint);
			} catch (CertificateEncodingException | NoSuchAlgorithmException e) {
				Trace.error("unable to compute digest for certificate", e);
			}
		}

		return null;
	}

	public String getAlias() {
		return alias;
	}

	public Certificate getCertificate() {
		return certificate;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

	public PrivateKey getPrivateKey() {
		return privateKey;
	}

	public SecretKey getSecretKey() {
		return secretKey;
	}

	public JWK getPublicJWK() {
		return publicJWK;
	}

	public JWK getJWK() {
		return privateJWK;
	}

	public String getKeyID() {
		return privateJWK == null ? null : privateJWK.getKeyID();
	}

	public String getX5T() {
		return x5t;
	}

	public String getX5T256() {
		return x5t256;
	}

	public boolean isX509Certificate() {
		return getCertificate() instanceof X509Certificate;
	}

	public Certificate[] getCertificateChain(KeyStorePathBuilder trust) throws CircuitAbortException {
		Certificate[] chain = null;

		if (isX509Certificate()) {
			List<Certificate> path = new ArrayList<Certificate>();

			trust.getCertPathWithCache((X509Certificate) certificate, path);

			chain = path.toArray(new Certificate[0]);
		}

		return chain;
	}

	public CertPath getCertificatePath(KeyStorePathBuilder trust) throws CircuitAbortException {
		if (isX509Certificate()) {
			List<Certificate> path = new ArrayList<Certificate>();

			return trust.getCertPathWithCache((X509Certificate) certificate, path);
		}

		return null;
	}

	protected KeyStoreEntry refineCertificate(Certificate certificate, PrivateKey privateKey) {
		return new KeyStoreEntry(this, certificate, privateKey);
	}

	private static JWK refineJWK(String alias, Certificate certificate, PublicKey publicKey, PrivateKey privateKey) {
		JWK privateJWK = null;

		try {
			/* write keys to PEM so the JOSE parser can be used */
			StringWriter pem = new StringWriter();
			JcaPEMWriter writer = new JcaPEMWriter(pem);

			try {
				writer.writeObject(publicKey);

				if (privateKey != null) {
					writer.writeObject(privateKey);
				}
			} finally {
				writer.close();
			}

			privateJWK = JWK.parseFromPEMEncodedObjects(pem.toString());

			if (privateJWK instanceof RSAKey) {
				RSAKey.Builder builder = new RSAKey.Builder((RSAKey) privateJWK);

				if (alias != null) {
					builder = builder.keyID(alias);
				}

				if (certificate instanceof X509Certificate) {
					builder = builder.keyUse(KeyUse.from((X509Certificate) certificate));
					builder = builder.x509CertChain(Collections.singletonList(Base64.encode(certificate.getEncoded())));
					builder = builder.x509CertSHA256Thumbprint(getX5Tx(certificate, "SHA-256"));
				}

				privateJWK = builder.build();
			} else if (privateJWK instanceof ECKey) {
				ECKey.Builder builder = new ECKey.Builder((ECKey) privateJWK);

				if (alias != null) {
					builder = builder.keyID(alias);
				}

				if (certificate instanceof X509Certificate) {
					builder = builder.keyUse(KeyUse.from((X509Certificate) certificate));
					builder = builder.x509CertChain(Collections.singletonList(Base64.encode(certificate.getEncoded())));
					builder = builder.x509CertSHA256Thumbprint(getX5Tx(certificate, "SHA-256"));
				}

				privateJWK = builder.build();
			}
		} catch (IOException | CertificateEncodingException | JOSEException e) {
			Trace.error("unable to create JWK", e);
		}

		return privateJWK;
	}

	@SuppressWarnings("deprecation")
	private static Base64URL getX5T(JWK jwk) {
		return jwk.getX509CertThumbprint();
	}

	@Override
	public String getKey() {
		return getAlias();
	}

	@Override
	public Certificate getValue() {
		return getCertificate();
	}

	@Override
	public Certificate setValue(Certificate value) {
		/* entries are immutable */
		throw new UnsupportedOperationException();
	}

}
