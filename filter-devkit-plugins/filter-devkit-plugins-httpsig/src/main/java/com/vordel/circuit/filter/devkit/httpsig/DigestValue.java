package com.vordel.circuit.filter.devkit.httpsig;

import static com.vordel.circuit.filter.devkit.httpsig.SignatureTemplate.nonEmpty;
import static com.vordel.circuit.filter.devkit.httpsig.SignatureValue.split;
import static com.vordel.circuit.filter.devkit.httpsig.SignatureValue.unescape;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DigestValue<E> extends DigestTemplate<E> {
	private static final Pattern KEYPAIR = Pattern.compile("\\s*([^\\s=]+)\\s*=\\s*([A-Za-z0-9\\+/=]+)\\s*", Pattern.MULTILINE);

	private final byte[][] digests;

	public DigestValue(EntityParser<E> parser, DigestAlgorithm[] algorithms, byte[][] digests) {
		super(parser, algorithms);

		if ((digests == null) || (digests.length == 0)) {
			throw new IllegalArgumentException("digests are required.");
		}

		if (algorithms.length != digests.length) {
			throw new IllegalArgumentException("digest array length does not match algorithms length");
		}

		for (int index = 0; index < digests.length; index++) {
			if (digests[index] == null) {
				throw new IllegalArgumentException("no provided digest can be null");
			}
		}

		this.digests = digests;
	}

	public static <E, H> DigestValue<E> parseDigestHeader(EntityParser<E> eparser, HeaderParser<H> hparser, H headers) throws NoSuchAlgorithmException {
		List<String> pairs = getDigestPairs(hparser, headers);
		DigestValue<E> result = null;

		if (!pairs.isEmpty()) {
			List<String> algorithmList = new ArrayList<String>();
			List<String> encoded = new ArrayList<String>();

			for (String pair : pairs) {
				Matcher matcher = KEYPAIR.matcher(pair);

				if (matcher.matches()) {
					String algorithm = nonEmpty(matcher.group(1), false);
					String digest = unescape(nonEmpty(matcher.group(2), false));

					if ((algorithm != null) && (digest != null)) {
						algorithmList.add(algorithm);
						encoded.add(digest);
					}
				}
			}

			DigestAlgorithm[] algorithmsArray = getAlgorithms(algorithmList.toArray(new String[0]));
			List<byte[]> digests = new ArrayList<byte[]>();
			ListIterator<String> iterator = encoded.listIterator();

			while (iterator.hasNext()) {
				String digest = iterator.next();
				DigestAlgorithm algorithm = algorithmsArray[iterator.previousIndex()];

				digests.add(algorithm.decode(digest));
			}

			result = new DigestValue<E>(eparser, algorithmsArray, digests.toArray(new byte[0][]));
		}

		return result;
	}

	private static <H> List<String> getDigestPairs(HeaderParser<H> parser, H headers) {
		List<String> digests = parser.getHeaderValues(headers, "Digest");
		List<String> pairs = new ArrayList<String>();

		if (digests != null) {
			StringBuilder builder = new StringBuilder();

			for (String value : digests) {
				if (builder.length() > 0) {
					builder.append(',');
				}

				builder.append(value);
			}

			pairs = split(pairs, builder.toString());
		}

		return pairs;
	}

	public boolean verify(E entity) throws IOException {
		boolean verified = false;
		int count = 0;

		try {
			for (int index = 0; index < algorithms.length; index++) {
				byte[] digest = algorithms[index].digest(parser, entity);

				if ((digest != null) && Arrays.equals(digest, digests[index])) {
					count++;
				}
			}

			verified = count == algorithms.length;
		} catch (NoSuchAlgorithmException e) {
			/* no need to check further */
		}

		return verified;
	}

	public static <E, H> boolean verifyContentMD5(EntityParser<E> eparser, E entity, HeaderParser<H> hparser, H headers) throws IOException {
		List<String> digests = hparser.getHeaderValues(headers, "Content-MD5");
		String verify = null;

		if (digests != null) {
			Iterator<String> iterator = digests.iterator();

			if (iterator.hasNext()) {
				verify = iterator.next();

				if (iterator.hasNext()) {
					verify = null;
				}
			}
		}

		return verifyContentMD5(eparser, entity, verify);
	}

	public static <E> boolean verifyContentMD5(EntityParser<E> parser, E entity, String verify) throws IOException {
		byte[] digest = verify == null ? null : DigestAlgorithm.DIGEST_MD5.decode(verify);

		return verifyContentMD5(parser, entity, digest);
	}

	public static <E> boolean verifyContentMD5(EntityParser<E> parser, E entity, byte[] verify) throws IOException {
		boolean verified = false;

		if (verify != null) {
			try {
				byte[] digest = DigestAlgorithm.DIGEST_MD5.digest(parser, entity);

				verified = (digest != null) && Arrays.equals(digest, verify);
			} catch (NoSuchAlgorithmException e) {
				/* should not occur for MD5 */
			}
		}

		return verified;
	}

	public List<String> getDigestValues(List<String> values) {
		DigestAlgorithm[] algorithms = this.getAlgorithms();

		for (int index = 0; index < digests.length; index++) {
			DigestAlgorithm algorithm = algorithms[index];
			byte[] digest = digests[index];

			values.add(String.format("%s=%s", algorithm.getPortableName(), algorithm.encode(digest)));
		}

		return values;
	}

	@Override
	public String toString() {
		List<String> values = getDigestValues(new ArrayList<String>());
		StringBuilder builder = new StringBuilder();

		for (String value : values) {
			if (builder.length() > 0) {
				builder.append(',');
			}

			builder.append(value);
		}

		return builder.toString();
	}
}
