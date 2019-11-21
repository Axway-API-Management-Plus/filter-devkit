package com.vordel.sdk.samples.httpsig;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

public class SignatureValue<H> extends SignatureTemplate<H> {
	private static final Pattern AUTHORIZATION = Pattern.compile("Signature\\s+(\\S.*)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
	private static final Pattern KEYPAIR = Pattern.compile("\\s*([^\\s=]+)\\s*=\\s*\"((?:[^\"]|\\\\|\\\")*)\"\\s*", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

	private final byte[] signature;

	public SignatureValue(String keyId, SignatureAlgorithm algorithm, byte[] signature, List<String> headers, HeaderParser<H> parser) {
		super(keyId, algorithm, headers, parser);

		if (signature == null) {
			throw new IllegalArgumentException("signature is required.");
		}

		this.signature = signature;
	}

	public SignatureValue(String keyId, String algorithm, String signature, String headers, HeaderParser<H> parser) {
		this(keyId, getAlgorithm(algorithm), signature == null ? null : DatatypeConverter.parseBase64Binary(signature), getHeaders(headers), parser);
	}

	/**
	 * Parse the signature http header
	 * 
	 * @param <H> header object type
	 * @param headers header object
	 * @param parser header parser object
	 * @return an unverified {@link SignatureValue} object
	 */
	public static <H> SignatureValue<H> parseSignatureHeader(H headers, HeaderParser<H> parser) {
		List<String> values = parser.getHeaderValues(headers, "Signature");
		SignatureValue<H> signature = null;

		if ((values != null) && (values.size() == 1)) {
			signature = parseSignatureParameters(values.get(0), parser);
		}

		return signature;
	}

	/**
	 * Parse a signature authorization scheme
	 * 
	 * @param <H> header object type
	 * @param headers header object
	 * @param parser header parser object
	 * @return an unverified {@link SignatureValue} object
	 */
	public static <H> SignatureValue<H> parseSignatureAuthorization(H headers, HeaderParser<H> parser) {
		List<String> values = parser.getHeaderValues(headers, "Authorization");
		SignatureValue<H> signature = null;

		if ((values != null) && (values.size() == 1)) {
			String value = nonEmpty(values.get(0), false);

			if (value != null) {
				Matcher matcher = AUTHORIZATION.matcher(value);

				if (matcher.matches()) {
					value = matcher.group(1);

					signature = parseSignatureParameters(value, parser);
				}
			}
		}

		return signature;
	}

	/**
	 * parse signature parameters and returns a SignatureHeader object
	 * 
	 * @param <H> header object type
	 * @param header unparsed signature header
	 * @param parser HeaderParser which will be used in the newly created
	 *               SignatureHeader.
	 * @return a new signature header object. this method will throw an
	 *         IllegalArgumentException if parameters are not valid
	 */
	private static <H> SignatureValue<H> parseSignatureParameters(String header, HeaderParser<H> parser) {
		List<String> pairs = split(new ArrayList<String>(), header);
		Map<String, String> values = new HashMap<String, String>();

		for (String pair : pairs) {
			Matcher matcher = KEYPAIR.matcher(pair);

			if (matcher.matches()) {
				String key = nonEmpty(matcher.group(1), false);
				String value = unescape(nonEmpty(matcher.group(2), false));

				if ((key != null) && (value != null)) {
					values.put(key, value);
				}
			}
		}

		/* retrieve parameters and return the signature header object */
		String keyId = values.get("keyId");
		String signature = values.get("signature");
		String headers = values.get("headers");
		String algorithm = values.get("algorithm");

		return new SignatureValue<H>(keyId, algorithm, signature, headers, parser);
	}

	/**
	 * Unescape characters in quoted strings. invalid escapes will be handled as if
	 * there is no escape
	 * 
	 * @param value value to be unescaped
	 * @return unescaped value
	 */
	protected static String unescape(String value) {
		if (value != null) {
			StringBuilder out = new StringBuilder();
			boolean escape = false;

			for (char c : value.toCharArray()) {
				if (escape) {
					if ((c == '"') || (c == '\\')) {
						out.append(c);

						escape = false;
					} else {
						out.append('\\');
						out.append(c);
					}
				} else if (c == '\\') {
					escape = true;
				} else {
					out.append(c);
				}
			}

			if (escape) {
				out.append('\\');
			}

			value = out.toString();
		}

		return value;
	}

	/**
	 * this method will split signature parameters. It handles quoted commas and
	 * simple escape for characters '\' and '"'.
	 * 
	 * @param tokens mutable list of splitted tokens (output)
	 * @param value  the value to be splitted in the form <code>param="value"</code>
	 * @return the provided token list
	 */
	protected static List<String> split(List<String> tokens, String value) {
		StringBuilder item = new StringBuilder();
		boolean quoted = false;
		boolean escape = false;

		for (int index = 0; index < value.length(); index++) {
			char c = value.charAt(index);

			if (escape) {
				item.append(c);

				escape = false;
			} else if (quoted) {
				item.append(c);

				escape = c == '\\';
				quoted = c != '"';
			} else {
				if (c == ',') {
					tokens.add(item.toString());

					item = new StringBuilder();
				} else {
					item.append(c);

					quoted = c == '"';
				}
			}
		}

		if (item.length() > 0) {
			tokens.add(item.toString());
		}

		return tokens;
	}

	public byte[] getSignature() {
		return signature;
	}

	public boolean verify(String method, String uri, H headers, byte[] hmac) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException {
		Key key = new SecretKeySpec(hmac, getAlgorithm().getJvmName());

		return verify(method, uri, headers, key);
	}

	public boolean verify(String method, String uri, H headers, Key key) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException {
		byte[] signingBytes = createSigningBytes(method, uri, headers, null);

		return signingBytes == null ? false : getAlgorithm().verify(getSignature(), signingBytes, key);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		byte[] signature = getSignature();

		builder.append(super.toString());

		if (signature != null) {
			builder.append(String.format(", signature=\"%s\"", DatatypeConverter.printBase64Binary(signature)));
		}

		return builder.toString();
	}
}
