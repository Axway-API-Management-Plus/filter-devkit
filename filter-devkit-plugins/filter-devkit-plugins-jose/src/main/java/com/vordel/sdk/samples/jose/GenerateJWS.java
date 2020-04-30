package com.vordel.sdk.samples.jose;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.conversion.AddHTTPHeaderProcessor.AddHeadersWhere;
import com.vordel.circuit.ext.filter.quick.QuickFilterField;
import com.vordel.circuit.ext.filter.quick.QuickFilterType;
import com.vordel.circuit.ext.filter.quick.QuickJavaFilterDefinition;
import com.vordel.circuit.jwt.JWTException;
import com.vordel.circuit.jwt.JWTUtils;
import com.vordel.circuit.script.context.resources.PolicyResource;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.common.Dictionary;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.dwe.ByteArrayContentSource;
import com.vordel.dwe.ContentSource;
import com.vordel.dwe.DelayedESPK;
import com.vordel.el.Selector;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;
import com.vordel.mime.Body;
import com.vordel.mime.ContentType;
import com.vordel.mime.HeaderSet;
import com.vordel.security.cert.PersonalInfo;
import com.vordel.store.cert.CertStore;
import com.vordel.trace.Trace;

@QuickFilterType(name = "JWSGeneratorFilter", resources = "generate_signature.properties", ui = "generate_signature.xml")
public class GenerateJWS extends QuickJavaFilterDefinition {
	public static final MediaType APPLICATION_JWT_TYPE = MediaType.valueOf("application/jwt");
	public static final MediaType APPLICATION_JOSE_TYPE = MediaType.valueOf("application/jose");

	private static final int SIGNATURE_ALGORITHMLEVEL = 0;
	private static final int SIGNATURE_ALGORITHMFIXED = 1;

	private static final int SIGNATURE_KEYINSTORE = 0;
	private static final int SIGNATURE_KEYSELECTOR = 1;

	private static final int SIGNATURE_KEYID_ALIAS = 0;
	private static final int SIGNATURE_KEYID_X5T = 1;
	private static final int SIGNATURE_KEYID_X5T256 = 2;

	private static final int SIGNATURE_JWS = 0;
	private static final int SIGNATURE_JWT = 1;

	private int signatureAlgorithmType = SIGNATURE_ALGORITHMLEVEL;
	private Selector<Integer> signatureAlgorithmLevel = null;
	private Selector<String> signatureAlgorithmFixed = null;

	private int signatureKeyType = SIGNATURE_KEYINSTORE;
	private int signatureKeyIdType = SIGNATURE_KEYID_ALIAS;
	private Selector<Object> signatureKeySelector = null;
	private Selector<PrivateKey> signaturePrivateKeySelector = null;
	private Selector<Certificate[]> signatureKeyChainSelector = null;
	private Selector<String> signatureKeyIdSelector = null;
	private PersonalInfo info = null;
	private String keyId = null;

	private final List<Selector<String>> headerExtensions = new ArrayList<Selector<String>>();
	private final List<Selector<String>> payloadAudiences = new ArrayList<Selector<String>>();
	private PolicyResource headerExtensionCircuit = null;

	private Selector<String> generateX5U = null;
	private Selector<String> generateJKU = null;
	private Selector<Boolean> generateTYP = null;
	private Selector<Boolean> generateCTY = null;
	private Selector<Boolean> generateKID = null;
	private Selector<Boolean> generateX5T = null;
	private Selector<Boolean> generateX5T256 = null;
	private Selector<Boolean> generateX5C = null;
	private Selector<Boolean> generateJWK = null;

	private int signaturePayloadType = SIGNATURE_JWS;
	private Selector<String> signaturePayloadSelector = null;
	private PolicyResource payloadExtensionCircuit = null;
	private Selector<String> generateISS = null;
	private Selector<String> generateSUB = null;
	private Selector<String> generateJTI = null;
	private Selector<String> generateAUD = null;
	private Selector<Boolean> generateIAT = null;
	private Selector<Integer> generateNBF = null;
	private Selector<Integer> generateEXP = null;
	private Selector<String> generateOIDATH = null;
	private Selector<String> generateOIDCH = null;
	private Selector<Boolean> deflatePayload = null;

	private Selector<Boolean> signatureOutputBody = null;
	private Selector<Boolean> signatureOutputAttribute = null;
	private Selector<Boolean> signatureOutputHeader = null;
	private Selector<String> signatureOutputAttributeName = null;
	private Selector<String> signatureOutputHeaderName = null;
	private Selector<Boolean> overwriteExistingHeader = null;
	private Selector<String> signatureOutputHeaderLocation = null;

	@QuickFilterField(name = "signatureAlgorithmType", cardinality = "?", type = "integer", defaults = "0")
	private void setSignatureAlgorithmType(ConfigContext ctx, Entity entity, String field) {
		Integer signatureKeyType = SelectorResource.fromLiteral(entity.getStringValue(field), Integer.class, true).substitute(Dictionary.empty);

		this.signatureAlgorithmType = signatureKeyType == null ? SIGNATURE_ALGORITHMLEVEL : signatureKeyType.intValue();
	}

	@QuickFilterField(name = "signatureAlgorithmLevel", cardinality = "?", type = "integer", defaults = "256")
	private void setSignatureAlgorithmLevel(ConfigContext ctx, Entity entity, String field) {
		signatureAlgorithmLevel = SelectorResource.fromLiteral(entity.getStringValue(field), Integer.class, true);
	}

	@QuickFilterField(name = "signatureAlgorithmFixed", cardinality = "?", type = "string")
	private void setSignatureAlgorithm(ConfigContext ctx, Entity entity, String field) {
		signatureAlgorithmFixed = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	@QuickFilterField(name = "signatureCertificate", cardinality = "?", type = "^Certificate")
	private void setSignatureCertificate(ConfigContext ctx, Entity entity, String field) {
		DelayedESPK circuitPK = new DelayedESPK(entity.getReferenceValue(field));
		ESPK substitutedPK = circuitPK.substitute(com.vordel.common.Dictionary.empty);

		CertStore store = CertStore.getInstance();
		info = store.getPersonalInfo(substitutedPK);
	}

	@QuickFilterField(name = "signatureKeyId", cardinality = "?", type = "integer", defaults = "0")
	private void setSignatureKeyId(ConfigContext ctx, Entity entity, String field) {
		Integer signatureKeyIdType = SelectorResource.fromLiteral(entity.getStringValue(field), Integer.class, true).substitute(Dictionary.empty);

		this.signatureKeyIdType = signatureKeyIdType == null ? SIGNATURE_KEYID_ALIAS : signatureKeyIdType.intValue();
	}

	@QuickFilterField(name = "signatureKeyType", cardinality = "?", type = "integer", defaults = "0")
	private void setSignatureKeyType(ConfigContext ctx, Entity entity, String field) {
		Integer signatureKeyType = SelectorResource.fromLiteral(entity.getStringValue(field), Integer.class, true).substitute(Dictionary.empty);

		this.signatureKeyType = signatureKeyType == null ? SIGNATURE_KEYINSTORE : signatureKeyType.intValue();
	}

	@QuickFilterField(name = "signatureKeySelector", cardinality = "?", type = "string", defaults = "${signer.key}")
	private void setSignatureKeySelector(ConfigContext ctx, Entity entity, String field) {
		signatureKeySelector = SelectorResource.fromLiteral(entity.getStringValue(field), Object.class, true);
	}

	@QuickFilterField(name = "signaturePrivateKeySelector", cardinality = "?", type = "string", defaults = "${signer.private.key}")
	private void setSignaturePrivateKeySelector(ConfigContext ctx, Entity entity, String field) {
		signaturePrivateKeySelector = SelectorResource.fromLiteral(entity.getStringValue(field), PrivateKey.class, true);
	}

	@QuickFilterField(name = "signatureKeyChainSelector", cardinality = "?", type = "string", defaults = "${signer.key.chain}")
	private void setSignatureKeyChainSelector(ConfigContext ctx, Entity entity, String field) {
		signatureKeyChainSelector = SelectorResource.fromLiteral(entity.getStringValue(field), Certificate[].class, true);
	}

	@QuickFilterField(name = "signatureKeyIdSelector", cardinality = "?", type = "string", defaults = "${signer.keyId}")
	private void setSignatureKeyIdSelector(ConfigContext ctx, Entity entity, String field) {
		signatureKeyIdSelector = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	@QuickFilterField(name = "headerExtensions", cardinality = "*", type = "string")
	private void setHeaderExtensions(ConfigContext ctx, Entity entity, String field) {
		Collection<String> values = entity.getStringValues(field);

		headerExtensions.clear();

		if (values != null) {
			for (String value : values) {
				Selector<String> selector = SelectorResource.fromLiteral(value, String.class, true);

				if (selector != null) {
					headerExtensions.add(selector);
				}
			}
		}
	}

	@QuickFilterField(name = "headerExtensionCircuit", cardinality = "?", type = "^FilterCircuit")
	private void setHeaderExtensionCircuit(ConfigContext ctx, Entity entity, String field) {
		headerExtensionCircuit = new PolicyResource(ctx, entity, field);
	}

	@QuickFilterField(name = "generateTYP", cardinality = "?", type = "boolean", defaults = "0")
	private void setGenerateTYP(ConfigContext ctx, Entity entity, String field) {
		generateTYP = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "generateCTY", cardinality = "?", type = "boolean", defaults = "0")
	private void setGenerateCTY(ConfigContext ctx, Entity entity, String field) {
		generateCTY = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "generateKID", cardinality = "?", type = "boolean", defaults = "0")
	private void setGenerateKID(ConfigContext ctx, Entity entity, String field) {
		generateKID = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "generateX5T", cardinality = "?", type = "boolean", defaults = "0")
	private void setGenerateX5T(ConfigContext ctx, Entity entity, String field) {
		generateX5T = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "generateX5T256", cardinality = "?", type = "boolean", defaults = "1")
	private void setGenerateX5T256(ConfigContext ctx, Entity entity, String field) {
		generateX5T256 = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "generateX5C", cardinality = "?", type = "boolean", defaults = "0")
	private void setGenerateX5C(ConfigContext ctx, Entity entity, String field) {
		generateX5C = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "generateX5U", cardinality = "?", type = "string", defaults = "${certificate.rfc5080.url}")
	private void setGenerateX5U(ConfigContext ctx, Entity entity, String field) {
		generateX5U = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	@QuickFilterField(name = "generateJWK", cardinality = "?", type = "boolean", defaults = "0")
	private void setGenerateJWK(ConfigContext ctx, Entity entity, String field) {
		generateJWK = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "generateJKU", cardinality = "?", type = "string", defaults = "${jwkset.url}")
	private void setGenerateJKU(ConfigContext ctx, Entity entity, String field) {
		generateJKU = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	@QuickFilterField(name = "signaturePayloadType", cardinality = "?", type = "integer", defaults = "0")
	private void setSignaturePayloadType(ConfigContext ctx, Entity entity, String field) {
		Integer signaturePayloadType = SelectorResource.fromLiteral(entity.getStringValue(field), Integer.class, true).substitute(Dictionary.empty);

		this.signaturePayloadType = signaturePayloadType == null ? SIGNATURE_JWS : signaturePayloadType.intValue();
	}

	@QuickFilterField(name = "signaturePayloadSelector", cardinality = "?", type = "string", defaults = "${content.body}")
	private void setSignaturePayloadSelector(ConfigContext ctx, Entity entity, String field) {
		signaturePayloadSelector = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	@QuickFilterField(name = "generateISS", cardinality = "?", type = "string", defaults = "${http.request.url}")
	private void setGenerateISS(ConfigContext ctx, Entity entity, String field) {
		generateISS = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	@QuickFilterField(name = "generateSUB", cardinality = "?", type = "string", defaults = "${authentication.subject.id}")
	private void setGenerateSUB(ConfigContext ctx, Entity entity, String field) {
		generateSUB = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	@QuickFilterField(name = "generateAUD", cardinality = "?", type = "string")
	private void setGenerateAUD(ConfigContext ctx, Entity entity, String field) {
		generateAUD = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	@QuickFilterField(name = "payloadAudiences", cardinality = "*", type = "string")
	private void setPayloadAudiences(ConfigContext ctx, Entity entity, String field) {
		Collection<String> values = entity.getStringValues(field);

		payloadAudiences.clear();

		if (values != null) {
			for (String value : values) {
				Selector<String> selector = SelectorResource.fromLiteral(value, String.class, true);

				if (selector != null) {
					payloadAudiences.add(selector);
				}
			}
		}
	}

	@QuickFilterField(name = "generateJTI", cardinality = "?", type = "string", defaults = "${id}")
	private void setGenerateJTI(ConfigContext ctx, Entity entity, String field) {
		generateJTI = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	@QuickFilterField(name = "generateIAT", cardinality = "?", type = "boolean", defaults = "1")
	private void setGenerateIAT(ConfigContext ctx, Entity entity, String field) {
		generateIAT = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "generateNBF", cardinality = "?", type = "integer")
	private void setGenerateNBF(ConfigContext ctx, Entity entity, String field) {
		generateNBF = SelectorResource.fromLiteral(entity.getStringValue(field), Integer.class, true);
	}

	@QuickFilterField(name = "generateEXP", cardinality = "?", type = "integer")
	private void setGenerateEXP(ConfigContext ctx, Entity entity, String field) {
		generateEXP = SelectorResource.fromLiteral(entity.getStringValue(field), Integer.class, true);
	}

	@QuickFilterField(name = "generateOIDATH", cardinality = "?", type = "string", defaults = "${openid.accesstoken}")
	private void setGenerateOIDATH(ConfigContext ctx, Entity entity, String field) {
		generateOIDATH = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	@QuickFilterField(name = "generateOIDCH", cardinality = "?", type = "string", defaults = "${openid.code}")
	private void setGenerateOIDCH(ConfigContext ctx, Entity entity, String field) {
		generateOIDCH = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	@QuickFilterField(name = "payloadExtensionCircuit", cardinality = "?", type = "^FilterCircuit")
	private void setPayloadExtensionCircuit(ConfigContext ctx, Entity entity, String field) {
		payloadExtensionCircuit = new PolicyResource(ctx, entity, field);
	}

	@QuickFilterField(name = "deflatePayload", cardinality = "?", type = "boolean", defaults = "0")
	private void setDeflatePayload(ConfigContext ctx, Entity entity, String field) {
		deflatePayload = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "signatureOutputBody", cardinality = "?", type = "boolean", defaults = "0")
	private void setSignatureOutputBody(ConfigContext ctx, Entity entity, String field) {
		signatureOutputBody = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "signatureOutputAttribute", cardinality = "?", type = "boolean", defaults = "1")
	private void setSignatureOutputAttribute(ConfigContext ctx, Entity entity, String field) {
		signatureOutputAttribute = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "signatureOutputHeader", cardinality = "?", type = "boolean", defaults = "0")
	private void setSignatureOutputHeader(ConfigContext ctx, Entity entity, String field) {
		signatureOutputHeader = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "signatureOutputAttributeName", cardinality = "?", type = "string", defaults = "jwt.body")
	private void setSignatureOutputAttributeName(ConfigContext ctx, Entity entity, String field) {
		signatureOutputAttributeName = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	@QuickFilterField(name = "signatureOutputHeaderName", cardinality = "?", type = "string")
	private void setSignatureOutputHeaderName(ConfigContext ctx, Entity entity, String field) {
		signatureOutputHeaderName = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	@QuickFilterField(name = "overwriteExistingHeader", cardinality = "?", type = "boolean", defaults = "1")
	private void setOverwriteExistingHeader(ConfigContext ctx, Entity entity, String field) {
		overwriteExistingHeader = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "signatureOutputHeaderLocation", cardinality = "?", type = "string", defaults = "httpheaders")
	private void setSignatureOutputHeaderLocation(ConfigContext ctx, Entity entity, String field) {
		signatureOutputHeaderLocation = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, true);
	}

	private static boolean substituteBoolean(Selector<Boolean> selector, Dictionary dict) {
		Boolean result = selector == null ? false : selector.substitute(dict);

		return result == null ? false : result;
	}

	@Override
	public void attachFilter(ConfigContext ctx, Entity entity) {
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
					keyId = JOSEUtils.generateX5T(info.certificate, MessageDigest.getInstance("SHA-256")).toString();
					break;
				case SIGNATURE_KEYID_X5T:
				default:
					keyId = JOSEUtils.generateX5T(info.certificate, MessageDigest.getInstance("SHA-1")).toString();
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

	@Override
	public boolean invokeFilter(Circuit c, Message m) throws CircuitAbortException {
		Body saved = (Body) m.get(MessageProperties.CONTENT_BODY);
		JWSSignerFactory factory = createJWSSignerFactory(c, m, saved);
		JWSObject jws = createJWSObject(c, m, saved, factory.getJWSHeader());
		boolean result = false;

		try {
			jws.sign(factory.newJWSSigner());

			result = true;
		} catch (JOSEException e) {
			Trace.error("Can't sign JOSE Object", e);
		}

		String value = jws.serialize();

		if (substituteBoolean(signatureOutputBody, m)) {
			ContentType contentType = new ContentType(ContentType.Authority.MIME, (signaturePayloadType == SIGNATURE_JWS ? APPLICATION_JOSE_TYPE : APPLICATION_JWT_TYPE).toString());
			ContentSource source = new ByteArrayContentSource(value.getBytes(JOSEUtils.UTF8));
			HeaderSet headers = new HeaderSet();

			headers.addHeader(HttpHeaders.CONTENT_TYPE, contentType.getType());
			m.put(MessageProperties.CONTENT_BODY, Body.create(headers, contentType, source));
		}

		if (substituteBoolean(signatureOutputAttribute, m)) {
			String name = signatureOutputAttributeName == null ? null : signatureOutputAttributeName.substitute(m);

			if (name != null) {
				m.put(name, value);
			}
		}

		if (substituteBoolean(signatureOutputHeader, m)) {
			String name = signatureOutputHeaderName == null ? null : signatureOutputHeaderName.substitute(m);

			if (name != null) {
				AddHeadersWhere location = signatureOutputHeaderLocation == null ? null : AddHeadersWhere.valueOf(signatureOutputHeaderLocation.substitute(m));
				HeaderSet headers = null;

				if (location == AddHeadersWhere.legacy) {
					location = name.toLowerCase().startsWith("content-") ? AddHeadersWhere.body : AddHeadersWhere.httpheaders;
				}

				switch (location) {
				case body:
					headers = saved == null ? null : saved.getHeaders();
					break;
				case httpheaders:
					headers = (HeaderSet) m.get(MessageProperties.HTTP_HEADERS);

					if (headers == null) {
						m.put(MessageProperties.HTTP_HEADERS, headers = new HeaderSet());
					}
					break;
				case legacy:
				default:
					/* nothing more */
				}

				if (headers == null) {
					Trace.error("Unable to find HTTP headers");
				} else {
					if (substituteBoolean(overwriteExistingHeader, m)) {
						headers.setHeader(name, value);
					} else {
						headers.addHeader(name, value);
					}
				}
			}
		}

		return result;
	}

	protected JWSObject createJWSObject(Circuit c, Message m, Body saved, JWSHeader header) throws CircuitAbortException {
		JWSObject jws = null;

		if (signaturePayloadType == SIGNATURE_JWS) {
			boolean deflate = "DEF".equals(header.getCustomParams().get("zip"));
			Payload payload = JOSEUtils.readPayloadFromBody(saved, deflate);

			jws = new JWSObject(header, payload);
		} else {
			String json = signaturePayloadSelector == null ? null : signaturePayloadSelector.substitute(m);

			if (json == null) {
				throw new CircuitAbortException("no provided payload");
			}

			try {
				JWTClaimsSet original = JWTClaimsSet.parse(json);
				JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder(original);
				long time = System.currentTimeMillis();
				Integer nbf = generateNBF == null ? null : generateNBF.substitute(m);
				Integer exp = generateEXP == null ? null : generateEXP.substitute(m);

				if (substituteBoolean(generateIAT, m)) {
					claims.issueTime(new Date(time));
				}

				if (nbf != null) {
					time += nbf.longValue() * 1000L;

					claims.notBeforeTime(new Date(time));
				} else {
					Date notBefore = original.getNotBeforeTime();

					if (notBefore != null) {
						time = notBefore.getTime();
					}
				}

				if (exp != null) {
					time += exp.longValue() * 1000L;

					claims.expirationTime(new Date(time));
				}

				String issuer = generateISS == null ? null : generateISS.substitute(m);
				String subject = generateSUB == null ? null : generateSUB.substitute(m);
				String jti = generateJTI == null ? null : generateJTI.substitute(m);
				String ath = generateOIDATH == null ? null : JOSEUtils.getOpenIDHalfHash(header.getAlgorithm(), generateOIDATH.substitute(m));
				String ch = generateOIDCH == null ? null : JOSEUtils.getOpenIDHalfHash(header.getAlgorithm(), generateOIDCH.substitute(m));

				if ((issuer != null) && (!issuer.isEmpty())) {
					claims.issuer(issuer);
				}

				if ((subject != null) && (!subject.isEmpty())) {
					claims.subject(subject);
				}

				if ((jti != null) && (!jti.isEmpty())) {
					claims.jwtID(jti);
				}

				if ((ath != null) && (!ath.isEmpty())) {
					claims.claim("at_hash", ath);
				}

				if ((ch != null) && (!ch.isEmpty())) {
					claims.claim("c_hash", ch);
				}

				List<String> audience = new ArrayList<String>();
				String aud = generateAUD == null ? null : generateAUD.substitute(m);

				if ((aud != null) && (!aud.isEmpty())) {
					audience.add(aud);
				}

				for (Selector<String> item : payloadAudiences) {
					String value = item.substitute(m);

					if ((value != null) && (!value.isEmpty()) && (!audience.contains(value))) {
						audience.add(value);
					}
				}

				switch (audience.size()) {
				case 0:
					break;
				case 1:
					claims.audience(audience.get(0));
					break;
				default:
					claims.audience(audience);
				}

				jws = new SignedJWT(header, JOSEUtils.invokeExtensionCircuit(c, m, payloadExtensionCircuit, saved, claims.build(), (parsed) -> {
					return JWTClaimsSet.parse(parsed);
				}));
			} catch (ParseException e) {
				throw new CircuitAbortException("Unable to parse JWTClaimsSet", e);
			}
		}

		return jws;
	}

	protected JWSSignerFactory createJWSSignerFactory(Circuit c, Message m, Body saved) throws CircuitAbortException {
		JWSAlgorithm algorithm = null;

		Certificate certificate = null;
		Certificate[] chain = null;
		String kid = null;
		Object signer = null;

		if (signatureKeyType == SIGNATURE_KEYSELECTOR) {
			signer = signatureKeySelector == null ? null : signatureKeySelector.substitute(m);
			kid = signatureKeyIdSelector == null ? null : signatureKeyIdSelector.substitute(m);

			if (signer instanceof X509Certificate) {
				try {
					PersonalInfo info = JOSEUtils.getPersonalInfo((X509Certificate) signer);

					if (info == null) {
						certificate = (Certificate) signer;
						signer = signaturePrivateKeySelector == null ? null : signaturePrivateKeySelector.substitute(m);

						if (signer == null) {
							throw new CircuitAbortException("bad certificate (not in key store and no private key provided)");
						}
					} else {
						certificate = info.certificate;
						signer = info.privateKey;
					}

					chain = getCertificateChain(m, info);
					algorithm = getSignatureAlgorithm(m, info);
				} catch (CertificateEncodingException e) {
					throw new CircuitAbortException("bad certificate (can't encode)", e);
				} catch (NoSuchAlgorithmException e) {
					throw new CircuitAbortException("unexpected exception (should not occur)", e);
				}
			} else if (signer instanceof Certificate) {
				certificate = (Certificate) signer;
				signer = signaturePrivateKeySelector == null ? null : signaturePrivateKeySelector.substitute(m);
				chain = getCertificateChain(m, null);

				if (signer == null) {
					throw new CircuitAbortException("bad certificate (no private key provided)");
				}
			} else if (signer instanceof PublicKey) {
				algorithm = getSignatureAlgorithm(m, signer);
			} else if ((signer instanceof byte[]) || (signer instanceof String)) {
				if (signer instanceof String) {
					signer = ((String) signer).getBytes(JOSEUtils.UTF8);
				}

				algorithm = getSignatureAlgorithm(m, signer);

				if (signer instanceof byte[]) {
					try {
						signer = JOSEUtils.padKey(algorithm, (byte[]) signer);
					} catch (JOSEException e) {
						throw new CircuitAbortException("algorithm do not support byte keys", e);
					}
				}
			} else {
				throw new CircuitAbortException("Unrecognized key type");
			}
		} else if (signatureKeyType == SIGNATURE_KEYINSTORE) {
			certificate = info.certificate;
			signer = info.privateKey;
			chain = getCertificateChain(m, info);
			kid = keyId;

			algorithm = getSignatureAlgorithm(m, info);
		} else {
			throw new CircuitAbortException("Unrecognized key retrieval mode");
		}

		JWSHeader.Builder header = newJWSBuilder(algorithm, m, kid, certificate, chain);
		String cty = null;

		if (substituteBoolean(generateTYP, m)) {
			header = header.type(signaturePayloadType == SIGNATURE_JWT ? JOSEObjectType.JWT : JOSEObjectType.JOSE);
		}

		Set<String> crit = new LinkedHashSet<String>();

		for (Selector<String> claim : headerExtensions) {
			String value = claim.substitute(m);

			if ((value != null) && (!value.isEmpty())) {
				crit.add(value);
			}
		}

		if (signaturePayloadType == SIGNATURE_JWS) {
			MediaType media = JOSEUtils.getMediaType(m);

			if (media != null) {
				if (APPLICATION_JWT_TYPE.isCompatible(media)) {
					cty = APPLICATION_JWT_TYPE.getSubtype().toUpperCase();
				} else if (APPLICATION_JOSE_TYPE.isCompatible(media)) {
					cty = APPLICATION_JOSE_TYPE.getSubtype().toUpperCase();
				} else if (substituteBoolean(generateCTY, m)) {
					if ("application".equalsIgnoreCase(media.getType())) {
						cty = media.getSubtype();
					} else {
						cty = String.format("%s/%s", media.getType(), media.getSubtype());
					}
				}
			}

			if (substituteBoolean(deflatePayload, m)) {
				crit.add("zip");

				header.customParam("zip", "DEF");
			}
		}

		if (cty != null) {
			header = header.contentType(cty);
		}

		crit.remove("typ");
		crit.remove("cty");
		crit.remove("kid");
		crit.remove("x5t");
		crit.remove("x5t#256");
		crit.remove("x5c");
		crit.remove("x5u");
		crit.remove("jku");
		crit.remove("jwk");

		if (!crit.isEmpty()) {
			header = header.criticalParams(crit);
		}

		return new JWSSignerFactory(JOSEUtils.invokeExtensionCircuit(c, m, headerExtensionCircuit, saved, header.build(), (json) -> {
			return JWSHeader.parse(json);
		}), signer);
	}

	protected Certificate[] getCertificateChain(Message m, PersonalInfo info) {
		Certificate[] internal = info == null ? null : info.chain;
		Certificate[] external = signatureKeyChainSelector == null ? null : signatureKeyChainSelector.substitute(m);

		return ((external == null) || ((internal != null) && (internal.length >= external.length))) ? internal : external;
	}

	protected JWSAlgorithm getSignatureAlgorithm(Message m, Object signer) throws CircuitAbortException {
		String algorithm = null;
		JWSAlgorithm result = null;

		switch (signatureAlgorithmType) {
		case SIGNATURE_ALGORITHMLEVEL:
			if (signer instanceof PersonalInfo) {
				signer = ((PersonalInfo) signer).certificate;
			}

			if (signer instanceof Certificate) {
				signer = ((Certificate) signer).getPublicKey();
			}

			Integer level = signatureAlgorithmLevel == null ? null : signatureAlgorithmLevel.substitute(m);

			if (level == null) {
				throw new CircuitAbortException("For Automatic algorithm discovery, algorithm level must be specified");
			} else {
				switch (level) {
				case 256:
				case 384:
				case 512:
					break;
				default:
					throw new CircuitAbortException("Algorithm level is invalid (must be 256, 384 or 512");
				}
			}

			if (signer instanceof PublicKey) {
				if (signer instanceof RSAPublicKey) {
					algorithm = String.format("RS%d", level);
				} else if (signer instanceof ECPublicKey) {
					algorithm = String.format("ES%d", level);
				} else {
					throw new CircuitAbortException("Only RSA and EC keys are supported");
				}
			} else if (signer instanceof byte[] || signer instanceof String) {
				algorithm = String.format("HS%d", level);
			} else {
				throw new CircuitAbortException("For Automatic algorithm discovery, signer must be either a Certificate, a byte array or a String");
			}
			break;
		case SIGNATURE_ALGORITHMFIXED:
			if (signatureAlgorithmFixed != null) {
				algorithm = signatureAlgorithmFixed.substitute(m);
			}

			if (algorithm == null) {
				throw new CircuitAbortException("Can't substitute signature algorithm from selector");
			}
			break;
		default:
			throw new CircuitAbortException("Invalid signature algorithm type");
		}

		result = JWSAlgorithm.parse(algorithm);

		return result;
	}

	protected JWSHeader.Builder newJWSBuilder(JWSAlgorithm algorithm, Message m, String kid, Certificate certificate, Certificate[] chain) {
		JWSHeader.Builder builder = new JWSHeader.Builder(algorithm);
		boolean hasJWK = false;

		try {
			if (certificate instanceof X509Certificate) {
				if (substituteBoolean(generateJWK, m)) {
					PublicKey key = certificate.getPublicKey();

					try {
						if (key instanceof RSAPublicKey) {
							builder = builder.jwk(newRSAKeyBuilder(m, kid, (X509Certificate) certificate, chain).build());
							hasJWK = true;
						} else if (key instanceof ECPublicKey) {
							builder = builder.jwk(newECKeyBuilder(m, kid, (X509Certificate) certificate, chain).build());
							hasJWK = true;
						} else {
							Trace.error("Only RSA and EC keys are supported");
						}
					} catch (JOSEException e) {
						Trace.error("unable to generate jwk", e);
					}
				}
			}

			if (!hasJWK) {
				if ((kid != null) && substituteBoolean(generateKID, m)) {
					builder = builder.keyID(kid);
				}

				if (certificate != null) {
					if (substituteBoolean(generateX5T, m)) {
						try {
							builder = JOSEUtils.x509CertThumbprint(builder, JOSEUtils.generateX5T(certificate, MessageDigest.getInstance("SHA-1")));
						} catch (NoSuchAlgorithmException e) {
							Trace.error("Unable to create x5t thumbprint", e);
						}
					}

					if (substituteBoolean(generateX5T256, m)) {
						try {
							builder = builder.x509CertSHA256Thumbprint(JOSEUtils.generateX5T(certificate, MessageDigest.getInstance("SHA-256")));
						} catch (NoSuchAlgorithmException e) {
							Trace.error("Unable to create x5t#256 thumbprint", e);
						}
					}

					if ((chain != null) && substituteBoolean(generateX5C, m)) {
						try {
							List<Base64> x5c = new ArrayList<Base64>();

							for (Certificate item : chain) {
								x5c.add(Base64.encode(item.getEncoded()));
							}

							builder = builder.x509CertChain(x5c);
						} catch (CertificateEncodingException e) {
							Trace.error("Unable to create x5c certificate chain", e);
						}
					}

					if (generateX5U != null) {
						String x5u = generateX5U.substitute(m);

						if (x5u != null) {
							try {
								builder = builder.x509CertURL(new URI(x5u));
							} catch (URISyntaxException e) {
								Trace.error("Unable to create x5u uri", e);
							}
						}
					}
				}
			}

			if (generateJKU != null) {
				String jku = generateJKU.substitute(m);

				if (jku != null) {
					try {
						builder = builder.jwkURL(new URI(jku));
					} catch (URISyntaxException e) {
						Trace.error("Unable to create jku uri", e);
					}
				}
			}
		} catch (CertificateEncodingException e) {
			Trace.error("unable to encode certificate", e);
		}

		return builder;
	}

	protected RSAKey.Builder newRSAKeyBuilder(Message m, String kid, X509Certificate certificate, Certificate[] chain) throws CertificateEncodingException, JOSEException {
		RSAKey.Builder builder = new RSAKey.Builder(RSAKey.parse(certificate));

		builder = JOSEUtils.x509CertThumbprint(builder, null);
		builder = builder.keyID(null);
		builder = builder.x509CertURL(null);
		builder = builder.x509CertChain(null);
		builder = builder.x509CertSHA256Thumbprint(null);

		if ((kid != null) && substituteBoolean(generateKID, m)) {
			builder = builder.keyID(kid);
		}

		if (certificate != null) {
			if (substituteBoolean(generateX5T, m)) {
				try {
					builder = JOSEUtils.x509CertThumbprint(builder, JOSEUtils.generateX5T(certificate, MessageDigest.getInstance("SHA-1")));
				} catch (NoSuchAlgorithmException e) {
					Trace.error("Unable to create x5t thumbprint", e);
				}
			}

			if (substituteBoolean(generateX5T256, m)) {
				try {
					builder = builder.x509CertSHA256Thumbprint(JOSEUtils.generateX5T(certificate, MessageDigest.getInstance("SHA-256")));
				} catch (NoSuchAlgorithmException e) {
					Trace.error("Unable to create x5t#256 thumbprint", e);
				}
			}

			if ((chain != null) && substituteBoolean(generateX5C, m)) {
				try {
					List<Base64> x5c = new ArrayList<Base64>();

					for (Certificate item : chain) {
						x5c.add(Base64.encode(item.getEncoded()));
					}

					builder = builder.x509CertChain(x5c);
				} catch (CertificateEncodingException e) {
					Trace.error("Unable to create x5c certificate chain", e);
				}
			}

			if (generateX5U != null) {
				String x5u = generateX5U.substitute(m);

				if (x5u != null) {
					try {
						builder = builder.x509CertURL(new URI(x5u));
					} catch (URISyntaxException e) {
						Trace.error("Unable to create x5u uri", e);
					}
				}
			}

		}

		return builder;
	}

	protected ECKey.Builder newECKeyBuilder(Message m, String kid, X509Certificate certificate, Certificate[] chain) throws CertificateEncodingException, JOSEException {
		ECKey.Builder builder = new ECKey.Builder(ECKey.parse(certificate));

		builder = JOSEUtils.x509CertThumbprint(builder, null);
		builder = builder.keyID(null);
		builder = builder.x509CertURL(null);
		builder = builder.x509CertChain(null);
		builder = builder.x509CertSHA256Thumbprint(null);

		if ((kid != null) && substituteBoolean(generateKID, m)) {
			builder = builder.keyID(kid);
		}

		if (substituteBoolean(generateX5T, m)) {
			try {
				builder = JOSEUtils.x509CertThumbprint(builder, JOSEUtils.generateX5T(certificate, MessageDigest.getInstance("SHA-1")));
			} catch (NoSuchAlgorithmException e) {
				Trace.error("Unable to create x5t thumbprint", e);
			}
		}

		if (substituteBoolean(generateX5T256, m)) {
			try {
				builder = builder.x509CertSHA256Thumbprint(JOSEUtils.generateX5T(certificate, MessageDigest.getInstance("SHA-256")));
			} catch (NoSuchAlgorithmException e) {
				Trace.error("Unable to create x5t#256 thumbprint", e);
			}
		}

		if ((chain != null) && substituteBoolean(generateX5C, m)) {
			try {
				List<Base64> x5c = new ArrayList<Base64>();

				for (Certificate item : chain) {
					x5c.add(Base64.encode(item.getEncoded()));
				}

				builder = builder.x509CertChain(x5c);
			} catch (CertificateEncodingException e) {
				Trace.error("Unable to create x5c certificate chain", e);
			}
		}

		if (generateX5U != null) {
			String x5u = generateX5U.substitute(m);

			if (x5u != null) {
				try {
					builder = builder.x509CertURL(new URI(x5u));
				} catch (URISyntaxException e) {
					Trace.error("Unable to create x5u uri", e);
				}
			}
		}

		return builder;
	}

	public static class JWSSignerFactory {
		private final JWSHeader header;
		private final Object signer;

		public JWSSignerFactory(JWSHeader header, Object signer) {
			this.header = header;
			this.signer = signer;
		}

		public JWSHeader getJWSHeader() {
			return header;
		}

		public JWSSigner newJWSSigner() throws CircuitAbortException {
			JWSSigner result = null;

			if ((signer != null) && (header != null)) {
				try {
					JWSAlgorithm alg = header.getAlgorithm();

					if (signer instanceof PrivateKey) {
						PrivateKey key = (PrivateKey) signer;

						if (RSASSASigner.SUPPORTED_ALGORITHMS.contains(alg)) {
							result = new RSASSASigner(key);
						} else if (ECDSASigner.SUPPORTED_ALGORITHMS.contains(alg)) {
							ECPrivateKey ecPrivateKey = JWTUtils.translateToECPrivateKey(key);

							result = new ECDSASigner((ECPrivateKey) ecPrivateKey);
						} else {
							Trace.error("No Supported assymetric algorithm");
						}
					} else if (signer instanceof byte[]) {
						if (MACSigner.SUPPORTED_ALGORITHMS.contains(alg)) {
							result = new MACSigner((byte[]) signer);
						}
					}
				} catch (JOSEException e) {
					throw new CircuitAbortException("Unable to create Signer object", e);
				} catch (JWTException e) {
					throw new CircuitAbortException("Unable to create Signer object", e);
				}
			}

			return result;
		}
	}

	@Override
	public void detachFilter() {
	}

}
