package com.vordel.circuit.filter.devkit.certmgr.export;

import java.nio.ByteBuffer;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.util.Base64URL;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.certmgr.KeyStoreEntry;
import com.vordel.circuit.filter.devkit.certmgr.KeyStoreFilter;
import com.vordel.circuit.filter.devkit.certmgr.KeyStoreHolder;
import com.vordel.circuit.filter.devkit.certmgr.KeyStorePathBuilder;
import com.vordel.circuit.filter.devkit.certmgr.export.jaxrs.CertPathBodyWriter;
import com.vordel.circuit.filter.devkit.certmgr.export.jaxrs.DERCertificateBodyWriter;
import com.vordel.circuit.filter.devkit.certmgr.export.jaxrs.PEMCertificateBodyWriter;
import com.vordel.circuit.filter.devkit.certmgr.export.jaxrs.PEMCertificateChainBodyWriter;
import com.vordel.circuit.filter.devkit.certmgr.export.jaxrs.PEMPublicKeyBodyWriter;
import com.vordel.circuit.filter.devkit.context.resources.InvocableResource;
import com.vordel.circuit.filter.devkit.script.jaxrs.ScriptWebComponent;
import com.vordel.trace.Trace;

public class KeyStoreExportService implements InvocableResource {
	private static final ObjectMapper GLOBAL_MAPPER = new ObjectMapper();

	private final List<KeyStoreHolder> stores;
	private final KeyStorePathBuilder trust;

	private final List<KeyStoreExportTransform> transforms;
	private final List<KeyStoreFilter> filters;

	private final ScriptWebComponent service;
	private final boolean exportPrivate;

	private final List<KeyStoreEntry> exports = new ArrayList<KeyStoreEntry>();
	private long stamp = 0L;

	protected final Object sync = new Object();

	public KeyStoreExportService(List<KeyStoreHolder> exported, KeyStorePathBuilder trust, List<KeyStoreFilter> filters, List<KeyStoreExportTransform> transforms, boolean exportPrivate) {
		this.stores = exported;
		this.trust = trust;
		this.filters = filters;
		this.transforms = transforms;
		this.exportPrivate = exportPrivate;

		ScriptWebComponent.Builder builder = ScriptWebComponent.builder();

		builder.writer(CertPathBodyWriter.class);
		builder.writer(DERCertificateBodyWriter.class);
		builder.writer(PEMCertificateBodyWriter.class);
		builder.writer(PEMCertificateChainBodyWriter.class);
		builder.writer(PEMPublicKeyBodyWriter.class);

		this.service = builder.build(this);

		reload(true);
	}

	private void reload(boolean force) {
		synchronized (sync) {
			/* since certpath builder has all stores, do not check for exported reload */
			if (trust.reload(force)) {
				Map<ByteBuffer, KeyStoreEntry> certificates = new HashMap<ByteBuffer, KeyStoreEntry>();

				exports.clear();

				for (KeyStoreHolder holder : stores) {
					for (KeyStoreEntry entry : holder) {
						/* process entry if not already added */
						if (isExported(entry)) {
							exportEntry(certificates, entry);
						}
					}
				}

				for (KeyStoreEntry entry : certificates.values()) {
					exports.add(entry);
				}

				stamp = System.currentTimeMillis();
			}
		}
	}

	private void exportEntry(Map<ByteBuffer, KeyStoreEntry> certificates, KeyStoreEntry entry) {
		try {
			if (entry.isX509Certificate()) {
				Certificate certificate = entry.getCertificate();

				byte[] encoded = certificate.getEncoded();
				ByteBuffer key = ByteBuffer.wrap(encoded);

				if (!certificates.containsKey(key)) {
					List<Certificate> path = new ArrayList<Certificate>();

					trust.getCertPath((X509Certificate) entry.getCertificate(), null, path);

					exportChain(certificates, path);

					certificates.put(key, entry);
				}
			}

			exports.add(entry);
		} catch (CertificateEncodingException e) {
			Trace.error("unable to encode certificate", e);
		}
	}

	private void exportChain(Map<ByteBuffer, KeyStoreEntry> certificates, List<Certificate> path) throws CertificateEncodingException {
		for (Certificate certificate : path) {
			byte[] encoded = certificate.getEncoded();
			ByteBuffer key = ByteBuffer.wrap(encoded);

			if (!certificates.containsKey(key)) {
				KeyStoreEntry entry = trust.getTrustEntryByEncodedCertificate(encoded);

				if (entry != null) {
					certificates.put(key, entry);
				}
			}
		}
	}

	private boolean isExported(KeyStoreEntry entry) {
		Iterator<KeyStoreFilter> iterator = filters.iterator();
		boolean exported = true;

		while (exported && iterator.hasNext()) {
			KeyStoreFilter filter = iterator.next();

			exported &= filter.isExported(entry);
		}

		return exported;
	}

	private KeyStoreEntry getKeyStoreEntry(Predicate<KeyStoreEntry> predicate) {
		KeyStoreEntry entry = null;

		synchronized (sync) {
			reload(false);

			Iterator<KeyStoreEntry> iterator = exports.iterator();

			while ((entry == null) && (iterator.hasNext())) {
				KeyStoreEntry element = iterator.next();

				if (predicate.test(element)) {
					entry = element;
				}
			}

			if (entry == null) {
				throw new WebApplicationException(Status.NOT_FOUND);
			}
		}

		return entry;
	}

	private Response getCertificateResponse(Request request, Predicate<KeyStoreEntry> predicate) {
		KeyStoreEntry entry = null;
		Date lastModified = null;

		synchronized (sync) {
			entry = getKeyStoreEntry(predicate);
			lastModified = new Date(stamp);
		}

		ResponseBuilder builder = request.evaluatePreconditions(lastModified);

		if (builder == null) {
			builder = Response.ok(entry.getCertificate()).lastModified(lastModified);
		}

		return builder.build();
	}

	private Response getPublicKeyResponse(Request request, Predicate<KeyStoreEntry> predicate) {
		KeyStoreEntry entry = null;
		Date lastModified = null;

		synchronized (sync) {
			entry = getKeyStoreEntry(predicate);
			lastModified = new Date(stamp);
		}

		ResponseBuilder builder = request.evaluatePreconditions(lastModified);

		if (builder == null) {
			builder = Response.ok(entry.getPublicKey()).lastModified(lastModified);
		}

		return builder.build();
	}

	private Response getCertificateChainResponse(Request request, Predicate<KeyStoreEntry> predicate) throws CircuitAbortException {
		Certificate[] chain = null;
		Date lastModified = null;

		synchronized (sync) {
			KeyStoreEntry entry = getKeyStoreEntry(predicate);

			lastModified = new Date(stamp);
			chain = entry.getCertificateChain(trust);
		}

		ResponseBuilder builder = request.evaluatePreconditions(lastModified);

		if (builder == null) {
			builder = Response.ok(chain).lastModified(lastModified);
		}

		return builder.build();
	}

	private Response getCertificatePathResponse(Request request, Predicate<KeyStoreEntry> predicate) throws CircuitAbortException {
		Date lastModified = null;
		CertPath path = null;

		synchronized (sync) {
			KeyStoreEntry entry = getKeyStoreEntry(predicate);

			lastModified = new Date(stamp);
			path = entry.getCertificatePath(trust);
		}

		ResponseBuilder builder = request.evaluatePreconditions(lastModified);

		if (builder == null) {
			builder = Response.ok(path).lastModified(lastModified);
		}

		return builder.build();
	}

	@GET
	@Path("x509/x5t:{x5t}.cer")
	@Produces("application/pkix-cert")
	public Response getDERCertificateByX5T(@Context Request request, @PathParam("x5t") String x5t) {
		if ((x5t == null) || x5t.isEmpty()) {
			throw new WebApplicationException(Status.BAD_REQUEST);
		}

		return getCertificateResponse(request, (entry) -> {
			String key = entry.getX5T();

			return x5t.equals(key);
		});
	}

	@GET
	@Path("x509/x5t:{x5t}.pubkey")
	@Produces({ "application/x-pem-file", MediaType.TEXT_PLAIN })
	public Response getPEMPublicKeyByX5T(@Context Request request, @PathParam("x5t") String x5t) throws CircuitAbortException {
		if ((x5t == null) || x5t.isEmpty()) {
			throw new WebApplicationException(Status.BAD_REQUEST);
		}

		return getPublicKeyResponse(request, (entry) -> {
			String key = entry.getX5T();

			return x5t.equals(key);
		});
	}

	@GET
	@Path("x509/x5t:{x5t}.pem")
	@Produces({ "application/x-pem-file", MediaType.TEXT_PLAIN })
	public Response getPEMCertificateByX5T(@Context Request request, @PathParam("x5t") String x5t) throws CircuitAbortException {
		if ((x5t == null) || x5t.isEmpty()) {
			throw new WebApplicationException(Status.BAD_REQUEST);
		}

		return getCertificateChainResponse(request, (entry) -> {
			String key = entry.getX5T();

			return x5t.equals(key);
		});
	}

	@GET
	@Path("x509/x5t:{x5t}.pkipath")
	@Produces(CertPathBodyWriter.PKIX_PKIPATH)
	public Response getCertificatePathByX5T(@Context Request request, @PathParam("x5t") String x5t) throws CircuitAbortException {
		if ((x5t == null) || x5t.isEmpty()) {
			throw new WebApplicationException(Status.BAD_REQUEST);
		}

		return getCertificatePathResponse(request, (entry) -> {
			String key = entry.getX5T();

			return x5t.equals(key);
		});
	}

	@GET
	@Path("x509/x5t256:{x5t}.cer")
	@Produces("application/pkix-cert")
	public Response getDERCertificateByX5T256(@Context Request request, @PathParam("x5t") String x5t) {
		if ((x5t == null) || x5t.isEmpty()) {
			throw new WebApplicationException(Status.BAD_REQUEST);
		}

		return getCertificateResponse(request, (entry) -> {
			String key = entry.getX5T256();

			return x5t.equals(key);
		});
	}

	@GET
	@Path("x509/x5t256:{x5t}.pubkey")
	@Produces({ "application/x-pem-file", MediaType.TEXT_PLAIN })
	public Response getPEMPublicKeyByX5T256(@Context Request request, @PathParam("x5t") String x5t) throws CircuitAbortException {
		if ((x5t == null) || x5t.isEmpty()) {
			throw new WebApplicationException(Status.BAD_REQUEST);
		}

		return getPublicKeyResponse(request, (entry) -> {
			String key = entry.getX5T256();

			return x5t.equals(key);
		});
	}

	@GET
	@Path("x509/x5t256:{x5t}.pem")
	@Produces({ "application/x-pem-file", MediaType.TEXT_PLAIN })
	public Response getPEMCertificateByX5T256(@Context Request request, @PathParam("x5t") String x5t) throws CircuitAbortException {
		if ((x5t == null) || x5t.isEmpty()) {
			throw new WebApplicationException(Status.BAD_REQUEST);
		}

		return getCertificateChainResponse(request, (entry) -> {
			String key = entry.getX5T256();

			return x5t.equals(key);
		});
	}

	@GET
	@Path("x509/x5t256:{x5t}.pkipath")
	@Produces(CertPathBodyWriter.PKIX_PKIPATH)
	public Response getCertificatePathByX5T256(@Context Request request, @PathParam("x5t") String x5t) throws CircuitAbortException {
		if ((x5t == null) || x5t.isEmpty()) {
			throw new WebApplicationException(Status.BAD_REQUEST);
		}

		return getCertificatePathResponse(request, (entry) -> {
			String key = entry.getX5T256();

			return x5t.equals(key);
		});
	}

	@GET
	@Path("x509/alias:{alias}.cer")
	@Produces("application/pkix-cert")
	public Response getDERCertificateByAlias(@Context Request request, @PathParam("alias") String alias) {
		if ((alias == null) || alias.isEmpty()) {
			throw new WebApplicationException(Status.BAD_REQUEST);
		}

		return getCertificateResponse(request, (entry) -> {
			String key = entry.getAlias();

			return alias.equals(key);
		});
	}

	@GET
	@Path("x509/alias:{alias}.pubkey")
	@Produces({ "application/x-pem-file", MediaType.TEXT_PLAIN })
	public Response getPEMPublicKeyByAlias(@Context Request request, @PathParam("alias") String alias) throws CircuitAbortException {
		if ((alias == null) || alias.isEmpty()) {
			throw new WebApplicationException(Status.BAD_REQUEST);
		}

		return getPublicKeyResponse(request, (entry) -> {
			String key = entry.getAlias();

			return alias.equals(key);
		});
	}

	@GET
	@Path("x509/alias:{alias}.pem")
	@Produces({ "application/x-pem-file", MediaType.TEXT_PLAIN })
	public Response getPEMCertificateByAlias(@Context Request request, @PathParam("alias") String alias) throws CircuitAbortException {
		if ((alias == null) || alias.isEmpty()) {
			throw new WebApplicationException(Status.BAD_REQUEST);
		}

		return getCertificateChainResponse(request, (entry) -> {
			String key = entry.getAlias();

			return alias.equals(key);
		});
	}

	@GET
	@Path("x509/alias:{alias}.pkipath")
	@Produces(CertPathBodyWriter.PKIX_PKIPATH)
	public Response getCertificatePathByAlias(@Context Request request, @PathParam("alias") String alias) throws CircuitAbortException {
		if ((alias == null) || alias.isEmpty()) {
			throw new WebApplicationException(Status.BAD_REQUEST);
		}

		return getCertificatePathResponse(request, (entry) -> {
			String key = entry.getAlias();

			return alias.equals(key);
		});
	}

	@Path("x5ts")
	public X5TCollection getX5TCollection() {
		return new X5TCollection();
	}

	@Path("x5t256s")
	public X5TCollection getX5T256Collection() {
		return new X5T256Collection();
	}

	@GET
	@Path("jwkset")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getJWKSet(@Context Request request) {
		Set<String> aliases = new HashSet<String>();
		Set<String> x5ts = new HashSet<String>();
		Set<String> x5t256s = new HashSet<String>();
		Date lastModified = null;

		List<KeyStoreEntry> safe = new ArrayList<KeyStoreEntry>();
		List<JWK> jwks = new ArrayList<JWK>();

		synchronized (sync) {
			reload(false);

			Iterator<KeyStoreEntry> iterator = exports.iterator();

			while (iterator.hasNext()) {
				KeyStoreEntry entry = iterator.next();

				safe.add(entry);
			}

			lastModified = new Date(stamp);
		}

		Iterator<KeyStoreEntry> entries = safe.iterator();

		while (entries.hasNext()) {
			KeyStoreEntry entry = entries.next();
			JWK jwk = getTransformedJWK(entry);

			if (jwk != null) {
				String alias = jwk.getKeyID();
				boolean added = false;

				added |= appendJWK(jwks, jwk, aliases, alias, added);

				if (added || (alias == null)) {
					added |= appendJWK(jwks, jwk, x5t256s, jwk.getX509CertSHA256Thumbprint(), added);
					added |= appendJWK(jwks, jwk, x5ts, getX5T(jwk), added);
				}
			}
		}

		JWKSet jwkset = new JWKSet(jwks);
		ResponseBuilder builder = request.evaluatePreconditions(lastModified);

		if (builder == null) {
			try {
				ObjectNode tree = (ObjectNode) GLOBAL_MAPPER.readTree(jwkset.toString(false));

				builder = Response.ok(tree).lastModified(lastModified);
			} catch (JsonProcessingException e) {
				throw new WebApplicationException("Unable to encode jwk set", e, Status.INTERNAL_SERVER_ERROR);
			}
		}

		return builder.build();
	}

	@GET
	@Path("jwkset/kid:{kid}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getJWKByKid(@Context Request request, @PathParam("kid") String kid) {
		if ((kid == null) || kid.isEmpty()) {
			throw new WebApplicationException(Status.BAD_REQUEST);
		}

		return getJWKResponse(request, (entry) -> {
			String key = entry.getAlias();

			return kid.equals(key);
		});
	}

	@GET
	@Path("jwkset/x5t:{x5t}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getJWKByX5T(@Context Request request, @PathParam("x5t") String x5t) {
		if ((x5t == null) || x5t.isEmpty()) {
			throw new WebApplicationException(Status.BAD_REQUEST);
		}

		return getJWKResponse(request, (entry) -> {
			String key = entry.getX5T();

			return x5t.equals(key);
		});
	}

	@GET
	@Path("jwkset/x5t256:{x5t}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getJWKByX5T256(@Context Request request, @PathParam("x5t") String x5t) {
		if ((x5t == null) || x5t.isEmpty()) {
			throw new WebApplicationException(Status.BAD_REQUEST);
		}

		return getJWKResponse(request, (entry) -> {
			String key = entry.getX5T();

			return x5t.equals(key);
		});
	}

	private Response getJWKResponse(Request request, Predicate<KeyStoreEntry> predicate) {
		KeyStoreEntry entry = null;
		Date lastModified = null;

		synchronized (sync) {
			entry = getKeyStoreEntry(predicate);
			lastModified = new Date(stamp);
		}

		ResponseBuilder builder = request.evaluatePreconditions(lastModified);

		if (builder == null) {
			JWK jwk = getTransformedJWK(entry);

			if (jwk == null) {
				throw new WebApplicationException(Status.NOT_FOUND);
			}

			try {
				ObjectNode tree = (ObjectNode) GLOBAL_MAPPER.readTree(jwk.toJSONString());

				builder = Response.ok(tree).lastModified(lastModified);
			} catch (JsonProcessingException e) {
				throw new WebApplicationException("Unable to encode jwk", e, Status.INTERNAL_SERVER_ERROR);
			}
		}

		return builder.build();
	}

	private static boolean appendJWK(List<JWK> jwks, JWK jwk, Set<String> set, Base64URL key, boolean added) {
		return key == null ? added : appendJWK(jwks, jwk, set, key.toString(), added);
	}

	private static boolean appendJWK(List<JWK> jwks, JWK jwk, Set<String> set, String key, boolean added) {
		if ((key != null) && set.add(key) && (!added)) {
			added = jwks.add(jwk);
		}

		return added;
	}

	private JWK getTransformedJWK(KeyStoreEntry entry) {
		try {
			JWK jwk = exportPrivate ? entry.getJWK() : entry.getPublicJWK();
			ObjectNode json = (ObjectNode) GLOBAL_MAPPER.readTree(jwk.toJSONString());
			Iterator<KeyStoreExportTransform> iterator = transforms.iterator();

			while ((json != null) && iterator.hasNext()) {
				KeyStoreExportTransform transform = iterator.next();

				json = transform.transform(trust, entry, json);
			}

			if (json != null) {
				return JWK.parse(GLOBAL_MAPPER.writeValueAsString(json));
			}
		} catch (JsonProcessingException e) {
			Trace.error("unable to decode JWK as JSON", e);
		} catch (ParseException e) {
			Trace.error("unable to decode transformed JSON as JWK", e);
		}

		return null;
	}

	@SuppressWarnings("deprecation")
	private static Base64URL getX5T(JWK jwk) {
		return jwk.getX509CertThumbprint();
	}

	@Override
	public boolean invoke(Message m) throws CircuitAbortException {
		return service.service(m);
	}

	public class X5TCollection {
		private X5TCollection() {
		}

		protected String getX5T(KeyStoreEntry entry) {
			return entry.getX5T();
		}

		@GET
		@Produces(MediaType.APPLICATION_JSON)
		public Response getX5TList(@Context Request request) {
			Set<String> x5ts = new HashSet<String>();
			Date lastModified = null;

			synchronized (sync) {
				reload(false);

				Iterator<KeyStoreEntry> iterator = exports.iterator();

				while (iterator.hasNext()) {
					KeyStoreEntry entry = iterator.next();
					String x5t = getX5T(entry);

					if (x5t != null) {
						x5ts.add(x5t);
					}
				}

				lastModified = new Date(stamp);
			}

			ResponseBuilder builder = request.evaluatePreconditions(lastModified);

			if (builder == null) {
				ArrayNode entity = GLOBAL_MAPPER.createArrayNode();

				for (String key : x5ts) {
					if (key != null) {
						entity.add(key);
					}
				}

				builder = Response.ok(entity).lastModified(lastModified);
			}

			return builder.build();
		}

		@GET
		@Path("{x5t}.cer")
		@Produces("application/pkix-cert")
		public Response getDERCertificate(@Context Request request, @PathParam("x5t") String x5t) {
			return getDERCertificateByX5T(request, x5t);
		}

		@GET
		@Path("{x5t}.pubkey")
		@Produces({ "application/x-pem-file", MediaType.TEXT_PLAIN })
		public Response getPEMPublicKey(@Context Request request, @PathParam("x5t") String x5t) throws CircuitAbortException {
			return getPEMPublicKeyByX5T(request, x5t);
		}

		@GET
		@Path("{x5t}.pem")
		@Produces({ "application/x-pem-file", MediaType.TEXT_PLAIN })
		public Response getPEMCertificate(@Context Request request, @PathParam("x5t") String x5t) throws CircuitAbortException {
			return getPEMCertificateByX5T(request, x5t);
		}

		@GET
		@Path("{x5t}.pkipath")
		@Produces(CertPathBodyWriter.PKIX_PKIPATH)
		public Response getCertificatePath(@Context Request request, @PathParam("x5t") String x5t) throws CircuitAbortException {
			return getCertificatePathByX5T(request, x5t);
		}
	}

	public class X5T256Collection extends X5TCollection {
		private X5T256Collection() {
		}

		@Override
		protected String getX5T(KeyStoreEntry entry) {
			return entry.getX5T256();
		}

		@GET
		@Path("{x5t}.cer")
		@Produces("application/pkix-cert")
		@Override
		public Response getDERCertificate(@Context Request request, @PathParam("x5t") String x5t) {
			return getDERCertificateByX5T256(request, x5t);
		}

		@GET
		@Path("{x5t}.pubkey")
		@Produces({ "application/x-pem-file", MediaType.TEXT_PLAIN })
		@Override
		public Response getPEMPublicKey(@Context Request request, @PathParam("x5t") String x5t) throws CircuitAbortException {
			return getPEMPublicKeyByX5T256(request, x5t);
		}

		@GET
		@Path("{x5t}.pem")
		@Produces({ "application/x-pem-file", MediaType.TEXT_PLAIN })
		@Override
		public Response getPEMCertificate(@Context Request request, @PathParam("x5t") String x5t) throws CircuitAbortException {
			return getPEMCertificateByX5T256(request, x5t);
		}

		@GET
		@Path("{x5t}.pkipath")
		@Produces(CertPathBodyWriter.PKIX_PKIPATH)
		@Override
		public Response getCertificatePath(@Context Request request, @PathParam("x5t") String x5t) throws CircuitAbortException {
			return getCertificatePathByX5T256(request, x5t);
		}
	}
}
