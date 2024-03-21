package com.vordel.circuit.filter.devkit.httpsig;

import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import javax.crypto.spec.SecretKeySpec;

public class SignatureTemplate<H> {
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private final List<String> headers;
	private final SignatureAlgorithm algorithm;
	private final String keyId;

	private final HeaderParser<H> parser;

	public SignatureTemplate(String keyId, String algorithm, String headers, HeaderParser<H> parser) {
		this(keyId, getAlgorithm(algorithm), getHeaders(headers), parser);
	}

	public SignatureTemplate(String keyId, String algorithm, List<String> headers, HeaderParser<H> parser) {
		this(keyId, getAlgorithm(algorithm), headers, parser);
	}

	public SignatureTemplate(String keyId, SignatureAlgorithm algorithm, List<String> headers, HeaderParser<H> parser) {
		keyId = nonEmpty(keyId, false);

		if (keyId == null) {
			throw new IllegalArgumentException("keyId is required.");
		}

		if (algorithm == null) {
			throw new IllegalArgumentException("algorithm is required.");
		}

		if (parser == null) {
			throw new IllegalArgumentException("header parser is required.");
		}

		if (headers != null) {
			headers = new ArrayList<String>(headers);
		} else {
			headers = new ArrayList<String>();
		}

		ListIterator<String> iterator = headers.listIterator();
		Set<String> seen = new HashSet<String>();

		while (iterator.hasNext()) {
			String value = nonEmpty(iterator.next(), true);

			if ((value == null) || (!seen.add(value))) {
				iterator.remove();
			} else {
				iterator.set(value);
			}
		}

		if (headers.isEmpty()) {
			headers.add("date");
		}

		this.keyId = keyId;
		this.algorithm = algorithm;
		this.headers = Collections.unmodifiableList(headers);
		this.parser = parser;
	}

	protected static final String nonEmpty(String value, boolean lowercase) {
		if (value != null) {
			value = value.trim();

			if (lowercase) {
				value = value.toLowerCase();
			}

			if (value.isEmpty()) {
				value = null;
			}
		}

		return value;
	}

	protected static final SignatureAlgorithm getAlgorithm(String algorithm) {
		try {
			if (algorithm == null) {
				throw new IllegalArgumentException("Algorithm cannot be null");
			}

			return SignatureAlgorithm.get(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException("Algorithm is not supported", e);
		}
	}

	public static List<String> getHeaders(String headers) {
		String[] splitted = headers == null ? null : headers.split(" ");

		return splitted == null ? null : Arrays.asList(splitted);
	}

	public final String getKeyId() {
		return keyId;
	}

	public final SignatureAlgorithm getAlgorithm() {
		return algorithm;
	}

	public final List<String> getHeaders() {
		return headers;
	}

	public final boolean containsHeader(String header) {
		if (header != null) {
			header = header.toLowerCase();
		}

		return getHeaders().contains(header);
	}

	public final byte[] createSigningBytes(String method, String uri, H headers, List<String> missing) {
		String signingString = createSigningString(method, uri, headers, missing);

		return signingString == null ? null : signingString.getBytes(UTF8);
	}

	public String createSigningString(String method, String uri, H headers, List<String> missing) {
		List<String> required = getHeaders();

		StringBuilder builder = new StringBuilder();
		Iterator<String> keyIterator = required.iterator();

		method = nonEmpty(method, true);
		uri = nonEmpty(uri, false);

		while (keyIterator.hasNext()) {
			String key = keyIterator.next();
			String append = null;

			if ("(request-target)".equals(key)) {
				if ((method != null) && (uri != null)) {
					append = String.format("%s: %s %s", key, method.toLowerCase(), uri);
				}
			} else {
				String value = getValue(headers, key);

				if (value != null) {
					append = String.format("%s: %s", key, value);
				}
			}

			if (append != null) {
				if (builder.length() > 0) {
					builder.append('\n');
				}

				builder.append(append);
			} else if (missing != null) {
				missing.add(key);
			}
		}

		return builder.length() == 0 ? null : builder.toString();
	}

	private final String getValue(H headers, String name) {
		List<String> values = parser.getHeaderValues(headers, name);
		StringBuilder builder = new StringBuilder();

		if (values != null) {
			for (String value : values) {
				value = nonEmpty(value, false);

				if (value != null) {
					if (builder.length() > 0) {
						builder.append(", ");
					}

					builder.append(value);
				}
			}
		}

		return builder.length() == 0 ? null : builder.toString();
	}

	public SignatureValue<H> sign(String method, String uri, H headers, byte[] hmac, List<String> missing) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException {
		Key key = new SecretKeySpec(hmac, getAlgorithm().getJvmName());

		return sign(method, uri, headers, key, missing);
	}

	public SignatureValue<H> sign(String method, String uri, H headers, Key key, List<String> missing) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException {
		List<String> required = getHeaders();
		SignatureValue<H> result = null;
		byte[] signingBytes = null;

		if (missing == null) {
			missing = new ArrayList<String>();
		}

		/* create signature without missing headers headers */
		signingBytes = createSigningBytes(method, uri, headers, missing);
		required = new ArrayList<String>(required);
		required.removeAll(missing);

		if (signingBytes != null) {
			byte[] binarySignature = getAlgorithm().sign(signingBytes, key);

			if (binarySignature != null) {
				result = new SignatureValue<H>(getKeyId(), getAlgorithm(), binarySignature, required, parser);
			}
		}

		return result;
	}

	private final String escape(String value) {
		if (value != null) {
			StringBuilder out = new StringBuilder();

			for (char c : value.toCharArray()) {
				if ((c == '"') || (c == '\\')) {
					out.append('\\');
				}

				out.append(c);
			}

			value = out.toString();
		}

		return value;
	}

	@Override
	public String toString() {
		StringBuilder headers = new StringBuilder();

		for (String header : getHeaders()) {
			if (headers.length() > 0) {
				headers.append(' ');
			}

			headers.append(header);
		}

		return String.format("keyId=\"%s\", algorithm=\"%s\", headers=\"%s\"", escape(getKeyId()), escape(getAlgorithm().getPortableName()), escape(headers.toString()));
	}
}
