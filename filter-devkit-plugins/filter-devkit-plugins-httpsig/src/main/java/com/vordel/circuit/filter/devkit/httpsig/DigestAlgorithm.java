package com.vordel.circuit.filter.devkit.httpsig;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import com.vordel.circuit.filter.devkit.httpsig.algorithm.Cksum;
import com.vordel.circuit.filter.devkit.httpsig.algorithm.Sum16;

public enum DigestAlgorithm {
	DIGEST_MD5("MD5", "MD5"),
	DIGEST_SHA("SHA-1", "SHA", "SHA-1"),
	DIGEST_SUM(null, "UNIXsum"),
	DIGEST_CKSUM(null, "UNIXcksum"),
	DIGEST_SHA256("SHA-256", "SHA-256"),
	DIGEST_SHA384("SHA-384", "SHA-384"),
	DIGEST_SHA512("SHA-512", "SHA-512");

	private static final Map<String, DigestAlgorithm> publicAliases = new HashMap<String, DigestAlgorithm>();
	private static final Map<String, DigestAlgorithm> jvmAliases = new HashMap<String, DigestAlgorithm>();

	static {
		byte[] test = "test".getBytes();

		for (DigestAlgorithm algorithm : DigestAlgorithm.values()) {
			try {
				/* check that the algorithm can actually produce a hash */
				algorithm.digest(test);

				if (algorithm.jvmName != null) {
					jvmAliases.put(algorithm.jvmName, algorithm);
				}

				for (String portableName : algorithm.portableNames) {
					portableName = portableName.toLowerCase();

					publicAliases.put(portableName, algorithm);
				}
			} catch (UnsupportedOperationException e) {
			} catch (NoSuchAlgorithmException e) {
				/* in case of error, just not register the algorithm */
			}
		}
	}

	private final String[] portableNames;
	private final String jvmName;

	private DigestAlgorithm(String jvmName, String... digestNames) {
		this.portableNames = digestNames;
		this.jvmName = jvmName;
	}

	public final String getPortableName() {
		return portableNames[0];
	}

	public final String getJvmName() {
		return jvmName;
	}

	public static final String toPortableName(String name) throws NoSuchAlgorithmException {
		return get(jvmAliases, name).getPortableName();
	}

	public static final String toJvmName(String name) throws NoSuchAlgorithmException {
		return get(publicAliases, name == null ? null : name.toLowerCase()).getJvmName();
	}

	public static final DigestAlgorithm get(String name) throws NoSuchAlgorithmException {
		return get(publicAliases, name == null ? null : name.toLowerCase());
	}

	private static final DigestAlgorithm get(Map<String, DigestAlgorithm> aliases, String name) throws NoSuchAlgorithmException {
		final DigestAlgorithm algorithm = aliases.get(name);

		if (algorithm != null) {
			return algorithm;
		}

		throw new NoSuchAlgorithmException(name);
	}

	public byte[] digest(byte[] data) throws NoSuchAlgorithmException {
		byte[] digest = null;

		switch (this) {
		case DIGEST_SUM:
			digest = DigestOutputStream.digest(new Sum16(), data);
			break;
		case DIGEST_CKSUM:
			digest = DigestOutputStream.digest(new Cksum(), data);
			break;
		case DIGEST_MD5:
		case DIGEST_SHA:
		case DIGEST_SHA256:
		case DIGEST_SHA384:
		case DIGEST_SHA512:
		default:
			digest = MessageDigest.getInstance(getJvmName()).digest(data);
			break;
		}

		return digest;
	}

	public <E> byte[] digest(EntityParser<E> parser, E entity) throws NoSuchAlgorithmException, IOException {
		byte[] digest = null;

		try {
			switch (this) {
			case DIGEST_SUM:
				digest = DigestOutputStream.digest(new Sum16(), parser, entity);
				break;
			case DIGEST_CKSUM:
				digest = DigestOutputStream.digest(new Cksum(), parser, entity);
				break;
			case DIGEST_MD5:
			case DIGEST_SHA:
			case DIGEST_SHA256:
			case DIGEST_SHA384:
			case DIGEST_SHA512:
			default:
				digest = DigestOutputStream.digest(MessageDigest.getInstance(getJvmName()), parser, entity);
				break;
			}
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Unexpected error", e);
		}

		return digest;
	}

	public final String encode(byte[] digest) {
		String encoded = null;

		if (digest != null) {
			switch (this) {
			case DIGEST_SUM:
			case DIGEST_CKSUM:
				encoded = new BigInteger(1, digest).toString();
				break;
			case DIGEST_MD5:
			case DIGEST_SHA:
			case DIGEST_SHA256:
			case DIGEST_SHA384:
			case DIGEST_SHA512:
			default:
				encoded = DatatypeConverter.printBase64Binary(digest);
				break;
			}
		}

		return encoded;
	}

	public final byte[] decode(String digest) {
		byte[] decoded = null;

		if (digest != null) {
			switch (this) {
			case DIGEST_SUM:
				decoded = decodeInteger(this, digest, 2);
				break;
			case DIGEST_CKSUM:
				decoded = decodeInteger(this, digest, 4);
				break;
			case DIGEST_MD5:
			case DIGEST_SHA:
			case DIGEST_SHA256:
			case DIGEST_SHA384:
			case DIGEST_SHA512:
			default:
				decoded = DatatypeConverter.parseBase64Binary(digest);
				break;
			}
		}

		return decoded;
	}

	private static final byte[] decodeInteger(DigestAlgorithm algorithm, String digest, int length) {
		byte[] decoded = null;

		try {
			int value = new BigInteger(digest).intValue();

			decoded = new byte[length];

			for (int index = length - 1; index >= 0; index--) {
				decoded[index] = (byte) value;
				value >>>= 8;
			}
		} catch (NumberFormatException e) {

		}

		return decoded;
	}
}
