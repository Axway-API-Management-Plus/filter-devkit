package com.vordel.sdk.samples.jose;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.script.context.resources.PolicyResource;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.config.Circuit;
import com.vordel.el.Selector;
import com.vordel.mime.Body;
import com.vordel.mime.ContentType;
import com.vordel.mime.HeaderSet;
import com.vordel.mime.JSONBody;
import com.vordel.security.cert.PersonalInfo;
import com.vordel.store.cert.CertStore;

public class JOSEUtils {
	@FunctionalInterface
	public interface JOSEParser<T, R> {
		R apply(T t) throws ParseException;
	}

	private static final Selector<String> BODY_SELECTOR = SelectorResource.fromExpression(MessageProperties.CONTENT_BODY, String.class);
	private static final ObjectMapper MAPPER = new ObjectMapper();

	public static final Charset UTF8 = Charset.forName("UTF-8");

	public static final Base64URL generateX5T(Certificate certificate, MessageDigest digest) throws CertificateEncodingException {
		return Base64URL.encode(digest.digest(certificate.getEncoded()));
	}

	public static final String getOpenIDHalfHash(JWSAlgorithm sigAlg, String value) throws CircuitAbortException {
		return value == null ? null : getOpenIDHalfHash(sigAlg, value.getBytes(UTF8));
	}

	public static final String getOpenIDHalfHash(JWSAlgorithm sigAlg, byte[] bytes) throws CircuitAbortException {
		String hashAlg = null;
		String result = null;
	
		if (JWSAlgorithm.HS256.equals(sigAlg) || JWSAlgorithm.RS256.equals(sigAlg) || JWSAlgorithm.ES256.equals(sigAlg)) {
			hashAlg = "SHA-256";
		} else if (JWSAlgorithm.HS384.equals(sigAlg) || JWSAlgorithm.RS384.equals(sigAlg) || JWSAlgorithm.ES384.equals(sigAlg)) {
			hashAlg = "SHA-384";
		} else if (JWSAlgorithm.HS512.equals(sigAlg) || JWSAlgorithm.RS512.equals(sigAlg) || JWSAlgorithm.ES512.equals(sigAlg)) {
			hashAlg = "SHA-512";
		}
	
		if (hashAlg != null) {
			try {
				MessageDigest e = MessageDigest.getInstance(hashAlg);
	
				e.reset();
				e.update(bytes);
	
				byte[] hash = e.digest();
	
				int half = hash.length / 2;
	
				byte[] left = Arrays.copyOf(hash, half);
	
				result = Base64URL.encode(left).toString();
			} catch (NoSuchAlgorithmException e) {
				throw new CircuitAbortException("can't generate OpenID Hash", e);
			}
		}
	
		return result;
	}

	public static final byte[] padKey(JWSAlgorithm algorithm, byte[] key) throws JOSEException {
		int requested = MACSigner.getMinRequiredSecretLength(algorithm);
		int available = key.length * 8;
	
		if (requested > available) {
			/* pad the key with '0' or trim it if needed */
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ByteArrayInputStream inp = new ByteArrayInputStream(key);
	
			for (int read = -1; (requested > 0) && (read = inp.read()) != -1; requested -= 8) {
				out.write(read);
			}
	
			for (; requested > 0; requested -= 8) {
				out.write(0);
			}
	
			key = out.toByteArray();
		}
	
		return key;
	}

	public static final PersonalInfo getPersonalInfo(X509Certificate certificate) throws CertificateEncodingException, NoSuchAlgorithmException {
		String thumbprint = CertStore.getCertThumbprint(certificate);
	
		return CertStore.getInstance().getPersonalInfoByThumbprint(thumbprint);
	}

	public static final MediaType getMediaType(Message m) {
		Body body = (Body) m.get(MessageProperties.CONTENT_BODY);
		String media = null;
	
		if (body != null) {
			media = body.getHeaders().getHeader(HttpHeaders.CONTENT_TYPE);
	
			if (media == null) {
				media = body.getContentType().toString();
			}
		}
	
		return media == null ? null : MediaType.valueOf(media);
	}

	@SuppressWarnings("deprecation")
	public static final RSAKey.Builder x509CertThumbprint(RSAKey.Builder builder, Base64URL x5t) {
		return builder.x509CertThumbprint(x5t);
	}

	@SuppressWarnings("deprecation")
	public static final ECKey.Builder x509CertThumbprint(ECKey.Builder builder, Base64URL x5t) {
		return builder.x509CertThumbprint(x5t);
	}

	@SuppressWarnings("deprecation")
	public static final JWSHeader.Builder x509CertThumbprint(JWSHeader.Builder builder, Base64URL x5t) {
		return builder.x509CertThumbprint(x5t);
	}

	public static final <T> T invokeExtensionCircuit(Circuit c, Message m, PolicyResource resource, Body saved, T jose, JOSEParser<String, T> parser) throws CircuitAbortException {
		if ((resource != null) && (resource.getCircuit() != null)) {
			try {
				ContentType contentType = new ContentType(ContentType.Authority.MIME, "application/json");
				JsonNode node = MAPPER.readTree(jose.toString());
				HeaderSet headers = new HeaderSet();
	
				headers.addHeader(HttpHeaders.CONTENT_TYPE, contentType.getType());
	
				m.put(MessageProperties.CONTENT_BODY, new JSONBody(headers, contentType, node));
	
				try {
					if (resource.invoke(c, m)) {
						String json = BODY_SELECTOR.substitute(m);
	
						if (json == null) {
							throw new CircuitAbortException("The header processing policy did not return a result");
						}
	
						jose = parser.apply(json);
					} else {
						throw new CircuitAbortException("Extended processing failed");
					}
				} catch (ParseException e) {
					throw new CircuitAbortException("Unable to parse Extended processing result", e);
				} finally {
					if (saved == null) {
						m.remove(MessageProperties.CONTENT_BODY);
					} else {
						m.put(MessageProperties.CONTENT_BODY, saved);
					}
				}
			} catch (JsonProcessingException e) {
				throw new CircuitAbortException("Unable to transform header", e);
			} catch (IOException e) {
				throw new CircuitAbortException("Unable to transform header", e);
			}
		}
	
		return jose;
	}

	public static final Payload readPayloadFromBody(Body body, boolean deflate) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		Payload result = null;
		try {
			OutputStream deflated = output;
	
			if (deflate) {
				Deflater deflater = new Deflater(Deflater.DEFLATED, true);
	
				deflated = new DeflaterOutputStream(output, deflater);
			}
	
			try {
				body.write(deflated, Body.WRITE_NO_CTE);
			} finally {
				deflated.close();
			}
	
			result = new Payload(output.toByteArray());
		} catch (IOException e) {
			/*
			 * we are using byte arrays, IOExceptions should not occur
			 */
			throw new IllegalStateException(e);
		}
	
		return result;
	}

}
