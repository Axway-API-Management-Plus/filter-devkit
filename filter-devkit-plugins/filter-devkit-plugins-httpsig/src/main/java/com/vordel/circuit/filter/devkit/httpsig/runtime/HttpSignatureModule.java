package com.vordel.circuit.filter.devkit.httpsig.runtime;

import java.net.URI;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.filter.devkit.context.annotations.DictionaryAttribute;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionContext;
import com.vordel.circuit.filter.devkit.context.annotations.InvocableMethod;
import com.vordel.circuit.filter.devkit.context.annotations.SelectorExpression;
import com.vordel.circuit.filter.devkit.httpsig.DigestAlgorithm;
import com.vordel.circuit.filter.devkit.httpsig.DigestTemplate;
import com.vordel.circuit.filter.devkit.httpsig.SignatureTemplate;
import com.vordel.circuit.filter.devkit.httpsig.SignatureValue;
import com.vordel.mime.Body;
import com.vordel.security.cert.PersonalInfo;
import com.vordel.store.cert.CertStore;
import com.vordel.trace.Trace;

@ExtensionContext("http.signature")
public class HttpSignatureModule {
	@InvocableMethod("Authenticate")
	public static boolean validateHttpAuthorization(Message msg) throws CircuitAbortException {
		boolean result = false;

		try {
			SignatureValue<Message> signature = SignatureValue.parseSignatureAuthorization(msg, HttpMessageParser.PARSER);

			if (signature != null) {
				result = validateHttpSignature(msg, signature);

				if (result) {
					/*
					 * if signature has been successfully verified, update message like the SSL
					 * Client Cert Authentication filter
					 */
					X509Certificate certificate = (X509Certificate) msg.get("httpsig.signature.certificate");

					if (certificate != null) {
						List<X509Certificate> certs = new ArrayList<X509Certificate>();
						String subjectName = certificate.getSubjectDN().getName();
						String issuerName = certificate.getIssuerDN().getName();

						certs.add(certificate);
						msg.put("certificates", certs);
						msg.put("certificate", certificate);
						msg.put("authentication.method", "HTTP Signature client authentication");

						if (subjectName != null) {
							msg.put("authentication.subject.format", "X509DName");
							msg.put(MessageProperties.AUTHN_SUBJECT_ID, subjectName);
						}

						if (issuerName != null) {
							msg.put("authentication.issuer.format", "X509DName");
							msg.put("authentication.issuer.id", issuerName);
						}
					}
				}
			}
		} catch (IllegalArgumentException e) {
			if (Trace.isDebugEnabled()) {
				Trace.error("Unable to parse http signature authorization header", e);
			} else {
				Trace.error("Unable to parse http signature authorization header");
			}
		}

		return result;
	}

	@InvocableMethod("VerifySignature")
	public static boolean validateHttpSignature(Message msg, @SelectorExpression("httpsig.signature.failIfMissing") Boolean failIfMissing) throws CircuitAbortException {
		boolean result = false;

		try {
			if (failIfMissing == null) {
				failIfMissing = Boolean.TRUE;
			}

			SignatureValue<Message> signature = SignatureValue.parseSignatureHeader(msg, HttpMessageParser.PARSER);

			if (signature != null) {
				result = validateHttpSignature(msg, signature);
			} else {
				Trace.info("no Signature header found");

				result = !failIfMissing.booleanValue();
			}
		} catch (IllegalArgumentException e) {
			if (Trace.isDebugEnabled()) {
				Trace.error("Unable to parse http signature header", e);
			} else {
				Trace.error("Unable to parse http signature header");
			}
		}

		return result;
	}

	private static boolean validateHttpSignature(Message msg, SignatureValue<Message> signature) {
		boolean result = false;

		try {
			String verb = (String) msg.get(MessageProperties.HTTP_REQ_VERB);
			URI uri = (URI) msg.get(MessageProperties.HTTP_REQ_URI);

			try {
				PersonalInfo info = retrieveSignerKey(msg, signature.getKeyId());
				Key key = (info == null) || (info.certificate == null) ? null : info.certificate.getPublicKey();

				if (key != null) {
					result = signature.verify(verb, uri.toString(), msg, key);

					msg.put("httpsig.signature.certificate", info.certificate);
					msg.put("httpsig.signature.verified", result);
				}
			} catch (CircuitAbortException e) {
				Trace.info(String.format("Unable to retrieve Key for id '%s'", signature.getKeyId()));
			}
		} catch (InvalidKeyException e) {
			if (Trace.isDebugEnabled()) {
				Trace.error("can't generate http signature, signer key is invalid", e);
			} else {
				Trace.error("can't generate http signature, signer key is invalid");
			}
		} catch (NoSuchAlgorithmException e) {
			if (Trace.isDebugEnabled()) {
				Trace.error("can't generate http signature, algorithm does not exists", e);
			} else {
				Trace.error("can't generate http signature, algorithm does not exists");
			}
		} catch (SignatureException e) {
			if (Trace.isDebugEnabled()) {
				Trace.error("could not generate http signature, got unexpected signature exception", e);
			} else {
				Trace.error("could not generate http signature, got unexpected signature exception");
			}
		}

		return result;
	}

	@InvocableMethod("MD5")
	public static boolean generateContentMD5(Message msg, @DictionaryAttribute(MessageProperties.CONTENT_BODY) Body body) throws CircuitAbortException {
		return GenerateHttpDigestFilter.generateContentMD5(msg, body);
	}

	@InvocableMethod("VerifyMD5")
	public static boolean validateContentMD5(Message msg, @SelectorExpression("httpsig.md5.failIfMissing") Boolean failIfMissing) {
		return ValidateHttpDigestFilter.validateContentMD5(msg, failIfMissing == null ? Boolean.TRUE : failIfMissing.booleanValue());
	}

	@InvocableMethod("Digest")
	public static boolean generateEntityDigest(Message msg, @DictionaryAttribute(MessageProperties.CONTENT_BODY) Body body, @SelectorExpression("httpsig.digest.algorithms") String algorithms) throws CircuitAbortException {
		return GenerateHttpDigestFilter.generateEntityDigest(msg, body, DigestTemplate.getAlgorithms(algorithms));
	}

	@InvocableMethod("VerifyDigest")
	public static boolean validateEntityDigest(Message msg, @SelectorExpression("httpsig.digest.failIfMissing") Boolean failIfMissing) {
		Set<DigestAlgorithm> algorithms = new HashSet<DigestAlgorithm>();
		boolean result = ValidateHttpDigestFilter.validateEntityDigest(msg, algorithms, failIfMissing == null ? Boolean.TRUE : failIfMissing.booleanValue());

		if (result) {
			msg.put("httpsig.digest.verified", !algorithms.isEmpty());
		}

		return result;
	}

	@InvocableMethod("Sign")
	public static boolean generateHttpSignature(Message msg, @DictionaryAttribute(MessageProperties.CONTENT_BODY) Body body, @SelectorExpression("httpsig.certificate") X509Certificate certificate, @SelectorExpression("httpsig.signature.algorithm") String algorithm, @SelectorExpression("httpsig.required") String required, @SelectorExpression("httpsig.required.ignoreMissing") Boolean ignoreMissing) throws CircuitAbortException {
		boolean result = false;

		try {
			String keyId = certificate == null ? null : CertStore.getCertThumbprint(certificate);
			PersonalInfo info = retrieveSignerKey(msg, keyId);

			result = GenerateHttpSignatureFilter.generateHttpSignature(msg, body, keyId, info, algorithm, SignatureTemplate.getHeaders(required), true, ((ignoreMissing != null) && ignoreMissing.booleanValue()));
		} catch (IllegalArgumentException e) {
			throw new CircuitAbortException("Invalid http signature parameters", e);
		} catch (NoSuchAlgorithmException e) {
			throw new CircuitAbortException("Invalid http signature algorithm", e);
		} catch (CertificateEncodingException e) {
			throw new CircuitAbortException("Invalid http signature certificate", e);
		}

		return result;
	}

	private static PersonalInfo retrieveSignerKey(Message msg, String keyId) throws CircuitAbortException {
		PersonalInfo info = CertStore.getInstance().getPersonalInfoByThumbprint(keyId);

		return info;
	}
}
