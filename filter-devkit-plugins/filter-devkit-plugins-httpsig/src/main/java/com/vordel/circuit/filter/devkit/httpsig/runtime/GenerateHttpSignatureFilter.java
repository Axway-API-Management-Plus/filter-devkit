package com.vordel.circuit.filter.devkit.httpsig.runtime;

import java.net.URI;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;

import javax.crypto.spec.SecretKeySpec;
import javax.ws.rs.ext.RuntimeDelegate;
import javax.ws.rs.ext.RuntimeDelegate.HeaderDelegate;

import com.nimbusds.jose.util.Base64URL;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.filter.devkit.context.resources.SelectorResource;
import com.vordel.circuit.filter.devkit.httpsig.SignatureTemplate;
import com.vordel.circuit.filter.devkit.httpsig.SignatureValue;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterField;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterType;
import com.vordel.common.Dictionary;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.dwe.DelayedESPK;
import com.vordel.el.Selector;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;
import com.vordel.mime.Body;
import com.vordel.mime.HeaderSet;
import com.vordel.security.cert.PersonalInfo;
import com.vordel.store.cert.CertStore;
import com.vordel.trace.Trace;

@QuickFilterType(name = "HttpSignatureFilter", icon = "xmlsig", category="Integrity", resources = "generate_signature.properties", page = "generate_signature.xml")
public class GenerateHttpSignatureFilter extends GenerateHttpDigestFilter {
	private static final HeaderDelegate<Date> DELEGATE = RuntimeDelegate.getInstance().createHeaderDelegate(Date.class);

	protected static final Selector<String> REQUEST_VERB = SelectorResource.fromExpression(MessageProperties.HTTP_REQ_VERB, String.class);
	protected static final Selector<URI> REQUEST_URI = SelectorResource.fromExpression(MessageProperties.HTTP_REQ_URI, URI.class);

	protected static final Charset UTF8 = Charset.forName("UTF-8");

	private static final int SIGNATURE_KEYINSTORE = 0;
	private static final int SIGNATURE_KEYSELECTOR = 1;

	private static final int SIGNATURE_KEYID_ALIAS = 0;
	private static final int SIGNATURE_KEYID_X5T = 1;
	private static final int SIGNATURE_KEYID_X5T256 = 2;

	private final List<Selector<String>> selectors = new ArrayList<Selector<String>>();;

	private int signatureKeyType = SIGNATURE_KEYINSTORE;
	private int signatureKeyIdType = SIGNATURE_KEYID_ALIAS;
	private Selector<Object> signatureKeySelector = null;
	private Selector<String> signatureKeyIdSelector = null;
	private Selector<String> signatureAlgorithm = null;
	private PersonalInfo info = null;
	private String keyId = null;

	private boolean addMissingDate = false;
	private boolean ignoreMissing = false;

	@QuickFilterField(name = "addMissingDate", cardinality = "?", type = "boolean", defaults = "1")
	private void setAddMissingDate(ConfigContext ctx, Entity entity, String field) {
		addMissingDate = entity.getBooleanValue(field);
	}

	@QuickFilterField(name = "ignoreMissingHeaders", cardinality = "?", type = "boolean")
	private void setIgnoreMissing(ConfigContext ctx, Entity entity, String field) {
		ignoreMissing = entity.getBooleanValue(field);
	}

	@QuickFilterField(name = "signatureAlgorithm", cardinality = "?", type = "string")
	private void setSignatureAlgorithm(ConfigContext ctx, Entity entity, String field) {
		signatureAlgorithm = new Selector<String>(entity.getStringValue(field), String.class);
	}

	@QuickFilterField(name = "signatureCertificate", cardinality = "?", type = "^Certificate")
	private void setSignatureCertificate(ConfigContext ctx, Entity entity, String field) {
		DelayedESPK circuitPK = new DelayedESPK(entity.getReferenceValue(field));
		ESPK substitutedPK = circuitPK.substitute(com.vordel.common.Dictionary.empty);

		CertStore store = CertStore.getInstance();
		info = store.getPersonalInfo(substitutedPK);
	}

	@QuickFilterField(name = "signatureKeyId", cardinality = "?", type = "integer")
	private void setSignatureKeyId(ConfigContext ctx, Entity entity, String field) {
		Integer signatureKeyIdType = new Selector<Integer>(entity.getStringValue(field), Integer.class).substitute(Dictionary.empty);

		this.signatureKeyIdType = signatureKeyIdType == null ? SIGNATURE_KEYID_ALIAS : signatureKeyIdType.intValue();
	}

	@QuickFilterField(name = "signatureKeyType", cardinality = "?", type = "integer")
	private void setSignatureKeyType(ConfigContext ctx, Entity entity, String field) {
		Integer signatureKeyType = new Selector<Integer>(entity.getStringValue(field), Integer.class).substitute(Dictionary.empty);

		this.signatureKeyType = signatureKeyType == null ? SIGNATURE_KEYINSTORE : signatureKeyType.intValue();
	}

	@QuickFilterField(name = "signatureKeySelector", cardinality = "?", type = "string", defaults="${signer.key}")
	private void setSignatureKeySelector(ConfigContext ctx, Entity entity, String field) {
		signatureKeySelector = new Selector<Object>(entity.getStringValue(field), Object.class);
	}

	@QuickFilterField(name = "signatureKeyIdSelector", cardinality = "?", type = "string", defaults="${signer.keyId}")
	private void setSignatureKeyIdSelector(ConfigContext ctx, Entity entity, String field) {
		signatureKeyIdSelector = new Selector<String>(entity.getStringValue(field), String.class);
	}

	@QuickFilterField(name = "signedHeaders", cardinality = "*", type = "string")
	private void setDigestAlgorithms(ConfigContext ctx, Entity entity, String field) {
		Collection<String> values = entity.getStringValues(field);

		if (values != null) {
			for (String value : values) {
				selectors.add(new Selector<String>(value, String.class));
			}
		}
	}

	@Override
	public void attachFilter(ConfigContext ctx, Entity entity) {
		super.attachFilter(ctx, entity);
		String keyId = null;

		if (signatureKeyType == SIGNATURE_KEYINSTORE) {
			CertStore store = CertStore.getInstance();

			try {
				switch (signatureKeyIdType) {
				case SIGNATURE_KEYID_ALIAS:
					for (Entry<String, PersonalInfo> entry : store.getAliasToInfo().entrySet()) {
						if (entry.getValue() == info) {
							keyId = entry.getKey();
						}
					}
					break;
				case SIGNATURE_KEYID_X5T256:
					keyId = generateX5T(info.certificate, MessageDigest.getInstance("SHA-256")).toString();
					break;
				case SIGNATURE_KEYID_X5T:
				default:
					keyId = generateX5T(info.certificate, MessageDigest.getInstance("SHA-1")).toString();
					break;
				}
			} catch (CertificateEncodingException e) {
				throw new EntityStoreException("Unable to compute certificate KeyID", e);
			} catch (NoSuchAlgorithmException e) {
				throw new EntityStoreException("Unable to compute certificate KeyID", e);
			}
		} else {
			info = null;
		}

		this.keyId = keyId;
	}

	private static final PersonalInfo getPersonalInfo(X509Certificate certificate) throws CertificateEncodingException, NoSuchAlgorithmException {
		String thumbprint = CertStore.getCertThumbprint(certificate);

		return CertStore.getInstance().getPersonalInfoByThumbprint(thumbprint);
	}

	private static Base64URL generateX5T(Certificate certificate, MessageDigest digest) throws CertificateEncodingException {
		return Base64URL.encode(digest.digest(certificate.getEncoded()));
	}

	@Override
	public boolean invokeFilter(Circuit c, Message m, MessageProcessor p) throws CircuitAbortException {
		/* generate digest if needed */
		boolean result = super.invokeFilter(c, m, p);

		if (result) {
			List<String> resolved = new ArrayList<String>();
			Body body = BODY_SELECTOR.substitute(m);

			for (Selector<String> selector : selectors) {
				String value = selector.substitute(m);

				if (value != null) {
					value = value.trim();

					if ((!value.isEmpty()) && (!resolved.contains(value))) {
						resolved.add(value);
					}
				}
			}

			if (signatureKeyType == SIGNATURE_KEYSELECTOR) {
				Object key = signatureKeySelector.substitute(m);
				String keyId = signatureKeyIdSelector.substitute(m);

				if (keyId == null) {
					throw new CircuitAbortException("Signature Key Id is null");
				}
				
				if (key instanceof X509Certificate) {
					try {
						PersonalInfo info = getPersonalInfo((X509Certificate) key);
						
						if (info == null) {
							throw new CircuitAbortException("bad certificate (not in key store)");
						}
						
						key = info.privateKey;
					} catch (CertificateEncodingException e) {
						throw new CircuitAbortException("bad certificate (can't encode)", e);
					} catch (NoSuchAlgorithmException e) {
						throw new CircuitAbortException("unexpected exception (should not occur)", e);
					}
				}

				if (key instanceof PrivateKey) {
					/* simple case (like certificate since we directly have the private key */
					result &= generateHttpSignature(m, body, keyId, (PrivateKey) key, signatureAlgorithm.substitute(m), resolved, addMissingDate, ignoreMissing);
				} else if ((key instanceof byte[]) || (key instanceof String)) {
					if (key instanceof String) {
						key = ((String) key).getBytes(UTF8);
					}

					result &= generateHttpSignature(m, body, keyId, new SecretKeySpec((byte[]) key, "MAC"), signatureAlgorithm.substitute(m), resolved, addMissingDate, ignoreMissing);
				} else {
					throw new CircuitAbortException("Unrecognized key type");
				}
			} else if (signatureKeyType == SIGNATURE_KEYINSTORE) {
				result &= generateHttpSignature(m, body, keyId, info, signatureAlgorithm.substitute(m), resolved, addMissingDate, ignoreMissing);
			} else {
				throw new CircuitAbortException("Unrecognized key retrieval mode");
			}
		}

		return result;
	}

	@Override
	public void detachFilter() {
		selectors.clear();

		signatureKeyType = SIGNATURE_KEYINSTORE;
		signatureKeyIdType = SIGNATURE_KEYID_ALIAS;

		signatureKeySelector = null;
		signatureKeyIdSelector = null;
		signatureAlgorithm = null;
		keyId = null;
		info = null;

		addMissingDate = false;
		ignoreMissing = false;

		super.detachFilter();
	}

	public static boolean generateHttpSignature(Message msg, Body body, String keyId, PersonalInfo info, String algorithm, List<String> required, boolean addDate, boolean ignoreMissing) throws CircuitAbortException {
		Key key = null;

		if (info != null) {
			key = info.privateKey;
		}

		return generateHttpSignature(msg, body, keyId, key, algorithm, required, addDate, ignoreMissing);
	}

	public static boolean generateHttpSignature(Message msg, Body body, String keyId, Key key, String algorithm, List<String> required, boolean addDate, boolean ignoreMissing) throws CircuitAbortException {
		boolean result = false;

		try {
			SignatureTemplate<Message> template = new SignatureTemplate<Message>(keyId, algorithm, required, HttpMessageParser.PARSER);
			List<String> missing = new ArrayList<String>();
			HeaderSet headers = null;

			if (addDate && template.containsHeader("Date") && (HttpMessageParser.PARSER.getHeaderValues(msg, "Date") == null)) {
				headers = HttpMessageParser.getMessageHeaders(msg, true);

				headers.addHeader("Date", DELEGATE.toString(new Date()));
			}

			String verb = REQUEST_VERB.substitute(msg);
			URI uri = REQUEST_URI.substitute(msg);

			SignatureValue<Message> signature = template.sign(verb, uri.toString(), msg, key, missing);

			if (missing.isEmpty() || ignoreMissing) {
				if (!missing.isEmpty()) {
					Trace.info(String.format("generating http signature without the folloging missing headers: %s", missing));
				}

				headers = HttpMessageParser.getMessageHeaders(msg, true);

				/* remove signature header from message and body */
				HttpMessageParser.removeHeader(headers, "Signature");
				HttpMessageParser.removeHeader(body, "Signature");

				headers.setHeader("Signature", signature.toString());

				/* signal signature success */
				result = true;
			} else {
				Trace.error(String.format("could not generate http signature due to the folloging missing headers: %s", missing));
			}
		} catch (IllegalArgumentException e) {
			throw new CircuitAbortException("Invalid http signature parameters", e);
		} catch (InvalidKeyException e) {
			throw new CircuitAbortException("Invalid http signature key", e);
		} catch (NoSuchAlgorithmException e) {
			throw new CircuitAbortException("Invalid http signature algorithm", e);
		} catch (SignatureException e) {
			throw new CircuitAbortException("Unable to compute http signature", e);
		}

		return result;
	}
}
