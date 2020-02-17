package com.vordel.sdk.samples.httpsig.filter;

import java.net.URI;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.nimbusds.jose.util.Base64URL;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.ext.filter.quick.QuickFilterField;
import com.vordel.circuit.ext.filter.quick.QuickFilterType;
import com.vordel.circuit.script.context.resources.PolicyResource;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.common.Dictionary;
import com.vordel.common.base64.Encoder;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.es.Entity;
import com.vordel.sdk.samples.httpsig.SignatureValue;
import com.vordel.security.cert.PersonalInfo;
import com.vordel.store.cert.CertStore;
import com.vordel.trace.Trace;

@QuickFilterType(name = "ValidateHttpSignatureFilter", resources = "validate_signature.properties", ui = "validate_signature.xml")
public class ValidateHttpSignatureFilter extends ValidateHttpDigestFilter {
	private static final int SIGNATURE_HEADER = 0;
	private static final int SIGNATURE_AUTHENTICATE = 1;

	private static final int SIGNATURE_KEYINSTORE = 0;
	private static final int SIGNATURE_KEYSELECTOR = 1;
	private static final int SIGNATURE_KEYCIRCUIT = 2;

	private static final int SIGNATURE_KEYID_ALIAS = 0;
	private static final int SIGNATURE_KEYID_X5T = 1;

	private int signatureType = SIGNATURE_HEADER;
	private int signatureKeyId = SIGNATURE_KEYID_ALIAS;
	private int signatureKeyType = SIGNATURE_KEYINSTORE;
	private final List<Selector<String>> requiredHeaders = new ArrayList<Selector<String>>();

	private Selector<String> signatureCircuitKeyId = null;
	private Selector<Object> signatureCircuitKey = null;
	private Selector<Object> signatureKeySelector = null;
	private PolicyResource signatureCircuit = null;

	@QuickFilterField(name = "signatureType", cardinality = "?", type = "integer", defaults = "0")
	private void setSignatureType(ConfigContext ctx, Entity entity, String field) {
		Integer signatureType = SelectorResource.fromLiteral(entity.getStringValue(field), Integer.class, true).substitute(Dictionary.empty);

		this.signatureType = signatureType == null ? SIGNATURE_HEADER : signatureType.intValue();
	}

	@QuickFilterField(name = "signatureKeyType", cardinality = "?", type = "integer", defaults = "0")
	private void setSignatureKeyType(ConfigContext ctx, Entity entity, String field) {
		Integer signatureKeyType = SelectorResource.fromLiteral(entity.getStringValue(field), Integer.class, true).substitute(Dictionary.empty);

		this.signatureKeyType = signatureKeyType == null ? SIGNATURE_KEYINSTORE : signatureKeyType.intValue();
	}

	@QuickFilterField(name = "signatureKeyId", cardinality = "?", type = "integer", defaults = "0")
	private void setSignatureKeyId(ConfigContext ctx, Entity entity, String field) {
		Integer signatureKeyId = SelectorResource.fromLiteral(entity.getStringValue(field), Integer.class, true).substitute(Dictionary.empty);

		this.signatureKeyId = signatureKeyId == null ? SIGNATURE_KEYID_ALIAS : signatureKeyId.intValue();
	}

	@QuickFilterField(name = "signatureCircuit", cardinality = "?", type = "^FilterCircuit")
	private void setSignatureCircuit(ConfigContext ctx, Entity entity, String field) {
		signatureCircuit = new PolicyResource(ctx, entity, field);
	}

	@QuickFilterField(name = "signatureCircuitKeyId", cardinality = "?", type = "string", defaults="signer.keyid")
	private void setSignatureCircuitKeyId(ConfigContext ctx, Entity entity, String field) {
		signatureCircuitKeyId = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	@QuickFilterField(name = "signatureCircuitKey", cardinality = "?", type = "string", defaults="${signer.key}")
	private void setSignatureCircuitKey(ConfigContext ctx, Entity entity, String field) {
		signatureCircuitKey = SelectorResource.fromLiteral(entity.getStringValue(field), Object.class, true);
	}

	@QuickFilterField(name = "signatureKeySelector", cardinality = "?", type = "string", defaults="${signer.key}")
	private void setSignatureKeySelector(ConfigContext ctx, Entity entity, String field) {
		signatureKeySelector = SelectorResource.fromLiteral(entity.getStringValue(field), Object.class, true);
	}

	@QuickFilterField(name = "requiredHeaders", cardinality = "*", type = "string")
	private void setDigestAlgorithms(ConfigContext ctx, Entity entity, String field) {
		Collection<String> values = entity.getStringValues(field);

		requiredHeaders.clear();

		if (values != null) {
			for (String value : values) {
				Selector<String> selector = SelectorResource.fromLiteral(value, String.class, true);

				if (selector != null) {
					requiredHeaders.add(selector);
				}
			}
		}
	}

	@Override
	public void attachFilter(ConfigContext ctx, Entity entity) {
		super.attachFilter(ctx, entity);
	}

	@Override
	public boolean invokeFilter(Circuit c, Message m) throws CircuitAbortException {
		/* start by validating entity digest */
		boolean result = super.invokeFilter(c, m);

		if (result) {
			SignatureValue<Message> signature = null;

			try {
				switch (signatureType) {
				case SIGNATURE_HEADER:
					signature = SignatureValue.parseSignatureHeader(m, HttpMessageParser.PARSER);
					break;
				case SIGNATURE_AUTHENTICATE:
					signature = SignatureValue.parseSignatureAuthorization(m, HttpMessageParser.PARSER);
					break;
				default:
					throw new CircuitAbortException("Unknown signature retrieval type");
				}

				result = validateSignature(c, m, signature);
			} catch (IllegalArgumentException e) {
				/* got an exception when parsing the signature, trace it and exit */
				if (Trace.isDebugEnabled()) {
					Trace.error("Unable to parse http signature header", e);
				} else {
					Trace.error("Unable to parse http signature header");
				}

				result = false;
			}
		}

		return result;
	}

	private boolean validateSignature(Circuit c, Message m, SignatureValue<Message> signature) throws CircuitAbortException {
		boolean result = false;

		if (signature == null) {
			/* do not signal error if no signed header is required */
			result = requiredHeaders.isEmpty();
		} else {
			Object signer = retrieveSignatureKey(c, m, signature);

			if (signer == null) {
				Trace.error("Unable to retrieve signer key");
			} else {
				Certificate authenticated = null;

				if (signer instanceof Certificate) {
					signer = (authenticated = (Certificate) signer).getPublicKey();
				}

				try {
					String method = GenerateHttpSignatureFilter.REQUEST_VERB.substitute(m);
					URI uri = GenerateHttpSignatureFilter.REQUEST_URI.substitute(m);
					boolean verified = false;

					if (signer instanceof Key) {
						verified = signature.verify(method, uri.toString(), m, (Key) signer);
					} else if ((signer instanceof byte[]) || (signer instanceof String)) {
						if (signer instanceof String) {
							signer = ((String) signer).getBytes(GenerateHttpSignatureFilter.UTF8);
						}

						verified = signature.verify(method, uri.toString(), m, (byte[]) signer);
					} else {
						Trace.error("Unhandled signer key");
					}
					
					if (verified) {
						/* signature is now verified... check for required signed headers */
						Set<String> required = new HashSet<String>();

						for (Selector<String> selector : requiredHeaders) {
							if (selector != null) {
								String name = selector.substitute(m);

								if ((name != null) && (!name.isEmpty())) {
									required.add(name.toLowerCase());
								}
							}
						}

						result = signature.getHeaders().containsAll(required);
					}
				} catch (InvalidKeyException e) {
					Trace.error("unable to validate signature", e);
				} catch (NoSuchAlgorithmException e) {
					Trace.error("unable to validate signature", e);
				} catch (SignatureException e) {
					Trace.error("unable to validate signature", e);
				}

				if (result && (authenticated instanceof X509Certificate) && (signatureType == SIGNATURE_AUTHENTICATE)) {
					/*
					 * If we did process the Authentication header and the key was a certificate,
					 * set attributes like the SSL authentication filter
					 */
					List<X509Certificate> certs = new ArrayList<X509Certificate>();
					String subjectName = ((X509Certificate) authenticated).getSubjectDN().getName();
					String issuerName = ((X509Certificate) authenticated).getIssuerDN().getName();

					certs.add((X509Certificate) authenticated);

					m.put("certificates", certs);
					m.put("certificate", authenticated);
					m.put("authentication.method", "HTTP Signature client authentication");

					if (subjectName != null) {
						m.put("authentication.subject.format", "X509DName");
						m.put(MessageProperties.AUTHN_SUBJECT_ID, subjectName);
					}

					if (issuerName != null) {
						m.put("authentication.issuer.format", "X509DName");
						m.put("authentication.issuer.id", issuerName);
					}
				}
			}
		}

		return result;
	}

	private Object retrieveSignatureKey(Circuit c, Message m, SignatureValue<Message> signature) throws CircuitAbortException {
		String keyId = signature.getKeyId();
		Object signer = null;

		if (keyId == null) {
			Trace.error("signature does not have a key");
		} else {
			switch (signatureKeyType) {
			case SIGNATURE_KEYINSTORE:
				signer = retrieveKeyFromStore(keyId);
				break;
			case SIGNATURE_KEYSELECTOR:
				signer = signatureKeySelector == null ? null : signatureKeySelector.substitute(m);
				break;
			case SIGNATURE_KEYCIRCUIT:
				signer = retrieveKeyFromCircuit(c, m, keyId);
				break;
			default:
				throw new CircuitAbortException("Unknown key retrieval type");
			}
		}

		return signer;
	}

	private X509Certificate retrieveKeyFromStore(String keyId) throws CircuitAbortException {
		CertStore store = CertStore.getInstance();
		PersonalInfo info = null;

		switch (signatureKeyId) {
		case SIGNATURE_KEYID_ALIAS:
			info = store.getPersonalInfoByAlias(keyId);
			break;
		case SIGNATURE_KEYID_X5T:
			info = store.getPersonalInfoByThumbprint(Encoder.encode(new Base64URL(keyId).decode()));
		default:
			throw new CircuitAbortException("Unknown key store retrieval type");
		}

		return info == null ? null : info.certificate;
	}

	private Object retrieveKeyFromCircuit(Circuit c, Message m, String keyId) throws CircuitAbortException {
		if ((signatureCircuit == null) || (signatureCircuit.getCircuit() == null)) {
			throw new CircuitAbortException("No Circuit configured for key retrieval");
		}

		String attributeName = signatureCircuitKeyId == null ? null : signatureCircuitKeyId.substitute(m);

		if (attributeName == null) {
			throw new CircuitAbortException("Could not resolve input attribute for key retrieval circuit");
		}

		if (signatureKeySelector == null) {
			throw new CircuitAbortException("No output selector configured for key retrieval circuit");
		}

		m.put(attributeName, keyId);

		if (!signatureCircuit.invoke(c, m)) {
			Trace.error("Key Retrieval circuit failed");

			return null;
		}

		return signatureCircuitKey.substitute(m);
	}

	@Override
	public void detachFilter() {
		super.detachFilter();
	}
}
