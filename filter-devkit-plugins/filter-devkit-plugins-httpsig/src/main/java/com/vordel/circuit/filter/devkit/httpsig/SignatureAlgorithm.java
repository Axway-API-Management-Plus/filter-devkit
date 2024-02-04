package com.vordel.circuit.filter.devkit.httpsig;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;

public enum SignatureAlgorithm {
	// hmac
	HMAC_SHA1("HmacSHA1", "hmac-sha1"),
	HMAC_SHA224("HmacSHA224", "hmac-sha224"),
	HMAC_SHA256("HmacSHA256", "hmac-sha256"),
	HMAC_SHA384("HmacSHA384", "hmac-sha384"),
	HMAC_SHA512("HmacSHA512", "hmac-sha512"),

	// rsa
	RSA_SHA1("SHA1withRSA", "rsa-sha1"),
	RSA_SHA256("SHA256withRSA", "rsa-sha256"),
	RSA_SHA384("SHA384withRSA", "rsa-sha384"),
	RSA_SHA512("SHA512withRSA", "rsa-sha512"),

	// dsa
	DSA_SHA1("SHA1withDSA", "dsa-sha1"),
	DSA_SHA224("SHA224withDSA", "dsa-sha224"),
	DSA_SHA256("SHA256withDSA", "dsa-sha256"),

	// ecc
	ECDSA_SHA1("SHA1withECDSA", "ecdsa-sha1"),
	ECDSA_SHA256("SHA256withECDSA", "ecdsa-sha256"),
	ECDSA_SHA384("SHA384withECDSA", "ecdsa-sha384"),
	ECDSA_SHA512("SHA512withECDSA", "ecdsa-sha512"),;

	private static final Map<String, SignatureAlgorithm> publicAliases = new HashMap<String, SignatureAlgorithm>();
	private static final Map<String, SignatureAlgorithm> jvmAliases = new HashMap<String, SignatureAlgorithm>();

	static {
		for (SignatureAlgorithm algorithm : SignatureAlgorithm.values()) {
			jvmAliases.put(normalize(algorithm.getJvmName()), algorithm);
			publicAliases.put(normalize(algorithm.getPortableName()), algorithm);
		}
	}

	private final String portableName;
	private final String jvmName;

	private SignatureAlgorithm(String jvmName, String portableName) {
		this.portableName = portableName;
		this.jvmName = jvmName;
	}

	public final String getPortableName() {
		return portableName;
	}

	public final String getJvmName() {
		return jvmName;
	}

	public static final String toPortableName(String name) throws NoSuchAlgorithmException {
		return get(jvmAliases, name).getPortableName();
	}

	public static final String toJvmName(String name) throws NoSuchAlgorithmException {
		return get(publicAliases, name).getJvmName();
	}

	public static final SignatureAlgorithm get(String name) throws NoSuchAlgorithmException {
		return get(publicAliases, name);
	}

	private static final SignatureAlgorithm get(Map<String, SignatureAlgorithm> aliases, String name) throws NoSuchAlgorithmException {
		final SignatureAlgorithm algorithm = aliases.get(normalize(name));

		if (algorithm != null)
			return algorithm;

		throw new NoSuchAlgorithmException(name);
	}

	private static String normalize(String algorithm) {
		return algorithm.replaceAll("[^A-Za-z0-9]+", "").toLowerCase();
	}

	public byte[] sign(byte[] signingBytes, Key key) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		byte[] signature = null;

		switch (this) {
		case HMAC_SHA1:
		case HMAC_SHA224:
		case HMAC_SHA256:
		case HMAC_SHA384:
		case HMAC_SHA512:
			signature = sign(Mac.getInstance(getJvmName()), signingBytes, key);
			break;
		case RSA_SHA1:
		case DSA_SHA1:
		case DSA_SHA224:
		case DSA_SHA256:
		case ECDSA_SHA1:
		case ECDSA_SHA256:
		case ECDSA_SHA384:
		case ECDSA_SHA512:
		case RSA_SHA256:
		case RSA_SHA384:
		case RSA_SHA512:
			signature = sign(Signature.getInstance(getJvmName()), signingBytes, key);
			break;
		default:
			throw new NoSuchAlgorithmException(String.format("Unknown Algorithm type %s", getPortableName()));
		}

		return signature;
	}

	public boolean verify(byte[] signature, byte[] signingBytes, Key key) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		boolean result = false;

		switch (this) {
		case HMAC_SHA1:
		case HMAC_SHA224:
		case HMAC_SHA256:
		case HMAC_SHA384:
		case HMAC_SHA512:
			result = verify(Mac.getInstance(getJvmName()), signature, signingBytes, key);
			break;
		case RSA_SHA1:
		case DSA_SHA1:
		case DSA_SHA224:
		case DSA_SHA256:
		case ECDSA_SHA1:
		case ECDSA_SHA256:
		case ECDSA_SHA384:
		case ECDSA_SHA512:
		case RSA_SHA256:
		case RSA_SHA384:
		case RSA_SHA512:
			result = verify(Signature.getInstance(getJvmName()), signature, signingBytes, key);
			break;
		default:
			throw new NoSuchAlgorithmException(String.format("Unknown Algorithm type %s", getPortableName()));
		}

		return result;
	}

	public static byte[] sign(Signature instance, byte[] signingBytes, Key key) throws InvalidKeyException, SignatureException {
		if (!(key instanceof PrivateKey)) {
			throw new InvalidKeyException("Provided Key is not Private");
		}

		instance.initSign((PrivateKey) key);
		instance.update(signingBytes);

		return instance.sign();
	}

	public static byte[] sign(Mac mac, byte[] signingBytes, Key key) throws InvalidKeyException {
		mac.init(key);

		return mac.doFinal(signingBytes);
	}

	public static boolean verify(Mac mac, byte[] signature, byte[] signingBytes, Key key) throws InvalidKeyException {
		mac.init(key);

		byte[] hash = mac.doFinal(signingBytes);

		return Arrays.equals(hash, signature);
	}

	public static boolean verify(Signature instance, byte[] signature, byte[] signingBytes, Key key) throws InvalidKeyException, SignatureException {
		if (!(key instanceof PublicKey)) {
			throw new InvalidKeyException("Provided Key is not public");
		}

		instance.initVerify((PublicKey) key);
		instance.update(signingBytes);

		return instance.verify(signature);
	}

	@Override
	public String toString() {
		return getPortableName();
	}
}
