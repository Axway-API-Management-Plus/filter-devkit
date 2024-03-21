package com.vordel.circuit.filter.devkit.httpsig;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DigestTemplate<E> {
	protected final DigestAlgorithm[] algorithms;
	protected final EntityParser<E> parser;

	public DigestTemplate(EntityParser<E> parser, String algorithms) throws NoSuchAlgorithmException {
		this(parser, getAlgorithms(algorithms));
	}

	public DigestTemplate(EntityParser<E> parser, String... algorithms) throws NoSuchAlgorithmException {
		this(parser, getAlgorithms(algorithms));
	}

	public DigestTemplate(EntityParser<E> parser, DigestAlgorithm... algorithms) {
		if ((algorithms == null) || (algorithms.length == 0)) {
			throw new IllegalArgumentException("algorithm is required.");
		}

		for (int index = 0; index < algorithms.length; index++) {
			if (algorithms[index] == null) {
				throw new IllegalArgumentException("no provided algorithm can be null");
			}
		}

		if (parser == null) {
			throw new IllegalArgumentException("entity parser is required.");
		}

		this.algorithms = algorithms;
		this.parser = parser;
	}

	public static String[] getAlgorithms(String algorithms) {
		String[] splitted = algorithms == null ? null : algorithms.split(" ");

		return splitted == null ? null : Arrays.asList(splitted).toArray(new String[0]);
	}

	protected static final DigestAlgorithm[] getAlgorithms(String... algorithms) throws NoSuchAlgorithmException {
		if (algorithms == null) {
			throw new IllegalArgumentException("Algorithm cannot be null");
		}

		List<DigestAlgorithm> result = new ArrayList<DigestAlgorithm>();

		for (int index = 0; index < algorithms.length; index++) {
			String algorithm = algorithms[index];

			if ((algorithm != null) && (!algorithm.isEmpty())) {
				result.add(DigestAlgorithm.get(algorithm));
			}
		}

		return result.toArray(new DigestAlgorithm[0]);
	}

	public final DigestAlgorithm[] getAlgorithms() {
		return algorithms;
	}

	public byte[][] digest(byte[] data) throws NoSuchAlgorithmException {
		List<byte[]> digests = new ArrayList<byte[]>(algorithms.length);

		if (data != null) {
			for (int index = 0; index < algorithms.length; index++) {
				digests.add(algorithms[index].digest(data));
			}
		}

		return digests.toArray(new byte[0][]);
	}

	public DigestValue<E> digest(E entity) throws NoSuchAlgorithmException, IOException {
		List<byte[]> digests = new ArrayList<byte[]>(algorithms.length);

		for (int index = 0; index < algorithms.length; index++) {
			byte[] digest = algorithms[index].digest(parser, entity);

			if (digest != null) {
				digests.add(digest);
			}
		}

		return digests.isEmpty() ? null : new DigestValue<E>(parser, getAlgorithms(), digests.toArray(new byte[0][]));
	}

	public static <E> String getContentMD5(EntityParser<E> parser, E entity) throws NoSuchAlgorithmException, IOException {
		byte[] digest = DigestAlgorithm.DIGEST_MD5.digest(parser, entity);

		return digest == null ? null : DigestAlgorithm.DIGEST_MD5.encode(digest);
	}

	@Override
	public String toString() {
		/* sounds a bit weird... but it avoids to use toString() on this object */
		throw new UnsupportedOperationException();
	}
}
