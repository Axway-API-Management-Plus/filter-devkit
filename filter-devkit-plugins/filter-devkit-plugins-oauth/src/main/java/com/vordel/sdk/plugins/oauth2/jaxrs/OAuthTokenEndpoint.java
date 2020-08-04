package com.vordel.sdk.plugins.oauth2.jaxrs;

import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_invalid_request;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_rfc6749_invalid_client;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_rfc6749_invalid_grant;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_rfc6749_invalid_scope;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_rfc6749_server_error;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_rfc6749_unauthorized_client;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_rfc6749_unsupported_grant_type;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_actor_token;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_actor_token_type;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_assertion;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_audience;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_client_assertion;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_client_assertion_type;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_client_id;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_client_secret;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_code;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_code_challenge;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_code_challenge_method;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_code_verifier;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_grant_type;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_password;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_redirect_uri;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_refresh_token;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_requested_token_type;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_resource;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_scope;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_subject_token;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_subject_token_type;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_username;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.uri_token_type_access_token;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.uri_token_type_refresh_token;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotAllowedException;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.POST;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.util.Base64URL;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.jaxrs.MultivaluedHeaderMap;
import com.vordel.circuit.oauth.kps.ApplicationDetails;
import com.vordel.circuit.oauth.provider.AuthorizationRequest;
import com.vordel.circuit.oauth.store.AuthorizationCodeStore;
import com.vordel.circuit.oauth.store.TokenStore;
import com.vordel.circuit.oauth.token.AuthorizationCode;
import com.vordel.circuit.oauth.token.OAuth2AccessToken;
import com.vordel.circuit.oauth.token.OAuth2Authentication;
import com.vordel.circuit.oauth.token.OAuth2RefreshToken;
import com.vordel.circuit.script.context.resources.PolicyResource;
import com.vordel.config.Circuit;
import com.vordel.sdk.plugins.oauth2.model.OAuthParameters;
import com.vordel.sdk.plugins.oauth2.model.ScopeSet;
import com.vordel.trace.Trace;

public abstract class OAuthTokenEndpoint extends OAuthAuthenticatedEndpoint {
	protected abstract boolean allowGET(Message msg);
	protected abstract boolean enableJsonPOST(Message msg);
	protected abstract boolean enableGrantTypeFilter(Message msg);
	protected abstract boolean skipUserConsent(Message msg);
	protected abstract boolean allowOpenIDScope(Message msg);
	protected abstract boolean isExtendedGrantType(Message msg, String grant_type);
	protected abstract boolean allowPublicClientCredentials(Message msg);

	protected abstract OAuthAccessTokenGenerator getOAuthAccessTokenGenerator();

	protected abstract PolicyResource getGrantAuthenticatorCircuit();
	protected abstract PolicyResource getGrantDecoderCircuit();

	protected static final Map<String, Set<String>> PARAMETER_NAMES;
	protected static final Set<String> DEFAULT_PARAMETER_NAMES = parameters(param_scope);

	public static final String GRANTTYPE_CODE = "authorization_code";
	public static final String GRANTTYPE_PASSWORD = "password";
	public static final String GRANTTYPE_CLIENT = "client_credentials";
	public static final String GRANTTYPE_REFRESH = "refresh_token";
	public static final String GRANTTYPE_JWT = "urn:ietf:params:oauth:grant-type:jwt-bearer";
	public static final String GRANTTYPE_SAML2 = "urn:ietf:params:oauth:grant-type:saml2-bearer";
	public static final String GRANTTYPE_TOKEN = "urn:ietf:params:oauth:grant-type:token-exchange";

	static {
		PARAMETER_NAMES = new HashMap<String, Set<String>>();

		/* standard grant_types */
		PARAMETER_NAMES.put(GRANTTYPE_CODE, parameters(param_code, param_redirect_uri, param_code_verifier));
		PARAMETER_NAMES.put(GRANTTYPE_PASSWORD, parameters(param_scope, param_username, param_password));
		PARAMETER_NAMES.put(GRANTTYPE_CLIENT, parameters(param_scope));
		PARAMETER_NAMES.put(GRANTTYPE_REFRESH, parameters(param_scope, param_refresh_token));

		/* assertion grant_type */
		PARAMETER_NAMES.put(GRANTTYPE_JWT, parameters(param_scope, param_assertion));
		PARAMETER_NAMES.put(GRANTTYPE_SAML2, parameters(param_scope, param_assertion));

		/* token exchange grant type */
		PARAMETER_NAMES.put(GRANTTYPE_TOKEN, parameters(param_resource, param_audience, param_scope, param_requested_token_type, param_subject_token, param_subject_token_type, param_actor_token, param_actor_token_type));
	}

	private static final Set<String> parameters(String... params) {
		Set<String> set = new HashSet<String>();

		set.add(param_grant_type);
		set.add(param_client_id);
		set.add(param_client_secret);
		set.add(param_client_assertion);
		set.add(param_client_assertion_type);

		for (String parameter : params) {
			set.add(parameter);
		}

		return Collections.unmodifiableSet(set);
	}

	public Set<String> getEndpointParameters(Message msg, String grant_type) {
		Set<String> names = PARAMETER_NAMES.get(grant_type);

		if (names == null) {
			if (!isExtendedGrantType(msg, grant_type)) {
				throw new OAuthException(err_rfc6749_unsupported_grant_type, null, "the provided grant_type is not supported");
			}

			return DEFAULT_PARAMETER_NAMES;
		} else {
			return names;
		}
	}

	private Set<String> getScopesForToken(Message msg, Circuit circuit, OAuthParameters parsed, ApplicationDetails details, OAuthAccessTokenGenerator generator, String subject, Set<String> additionalScopes) throws CircuitAbortException {
		Set<String> requestedScopes = generator.getScopesForToken(msg, circuit, parsed, details);
		
		msg.put("oauth.scopes.requested", requestedScopes);

		if ((!allowOpenIDScope(msg)) && requestedScopes.contains("openid")) {
			throw new OAuthException(err_rfc6749_invalid_scope, null, "scope 'openid' is not valid for token flows");
		}

		return generator.applyOwnerConsent(circuit, msg, subject, details, requestedScopes, additionalScopes, subject == null ? true : skipUserConsent(msg));
	}

	@GET
	public final Response serviceGET(@Context Circuit circuit, @Context Message msg, @Context HttpHeaders headers, @Context Request request, @Context UriInfo info) {
		if (!allowGET(msg)) {
			throw new NotAllowedException("Usage of GET is not allowed on this service");
		}

		return serviceFormPOST(circuit, msg, headers, request, info, null);
	}

	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	public final Response serviceFormPOST(@Context Circuit circuit, @Context Message msg, @Context HttpHeaders headers, @Context Request request, @Context UriInfo info, Form body) {
		MultivaluedMap<String, String> query = info.getQueryParameters();
		MultivaluedMap<String, String> form = body == null ? null : body.asMap();

		MultivaluedMap<String, String> merged = new Form().asMap();
		OAuthParameters parsed = new OAuthParameters(MAPPER.createObjectNode());

		/* start by merging query, body and header parameters */
		if (form != null) {
			merged = MultivaluedHeaderMap.mergeHeaders(merged, form);
		}
		
		merged = MultivaluedHeaderMap.mergeHeaders(merged, query);

		return service(msg, circuit, headers, request, info, parsed, MAPPER.createObjectNode(), cleanupHeaders(merged));
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public final Response serviceJsonPOST(@Context Circuit circuit, @Context Message msg, @Context HttpHeaders headers, @Context Request request, @Context UriInfo info, ObjectNode body) {
		if (!enableJsonPOST(msg)) {
			throw new NotSupportedException("Json payload is not supported");
		}

		MultivaluedMap<String, String> query = info.getQueryParameters();

		MultivaluedMap<String, String> merged = new Form().asMap();
		OAuthParameters parsed = new OAuthParameters(MAPPER.createObjectNode());

		/* start by merging query, body and header parameters */
		merged = MultivaluedHeaderMap.mergeHeaders(merged, query);

		return service(msg, circuit, headers, request, info, parsed, body, cleanupHeaders(merged));
	}
	@Override
	protected Response service(Message msg, Circuit circuit, HttpHeaders headers, Request request, UriInfo info, OAuthParameters parsed, ObjectNode body, MultivaluedMap<String, String> merged) {
		/* save http client headers */
		msg.put("oauth.request.parsed.headers", msg.get(MessageProperties.HTTP_HEADERS));

		/* set oauth request parsed parameters */
		msg.put("oauth.request.uri", getServiceURI(info));
		msg.put("oauth.request.parsed.mapped", parsed);
		msg.put("oauth.request.parsed.scopes", new ScopeSet(parsed.getObjectNode()));
		msg.put("oauth.request.parsed.json", parsed.getObjectNode());
		msg.put("oauth.request.extras.form", merged);
		msg.put("oauth.request.extras.json", body);

		OAuthParameters.register(parsed, param_grant_type);

		String grant_type = parsed.parse(param_grant_type, body, merged, null).asText(null);

		if (grant_type == null) {
			throw new OAuthException(err_invalid_request, null, "the grant_type parameter is mandatory");
		}

		/* this will throw an OAuth Exception if the grant type is not recognised */
		OAuthParameters.register(parsed, getEndpointParameters(msg, grant_type));

		/* set service method as early as possible */
		if (GRANTTYPE_CODE.equals(grant_type)) {
			msg.put(MessageProperties.SOAP_REQUEST_METHOD, "Exchange Authz Code for Access Token");
		} else if (GRANTTYPE_CLIENT.equals(grant_type)) {
			msg.put(MessageProperties.SOAP_REQUEST_METHOD, "Client Credentials");
		} else if (GRANTTYPE_JWT.equals(grant_type)) {
			msg.put(MessageProperties.SOAP_REQUEST_METHOD, "JWT Bearer");
		} else if (GRANTTYPE_SAML2.equals(grant_type)) {
			msg.put(MessageProperties.SOAP_REQUEST_METHOD, "SAML Bearer");
		} else if (GRANTTYPE_TOKEN.equals(grant_type)) {
			msg.put(MessageProperties.SOAP_REQUEST_METHOD, "Token Exchange");
		} else if (GRANTTYPE_REFRESH.equals(grant_type)) {
			msg.put(MessageProperties.SOAP_REQUEST_METHOD, "Refresh Access Token");
		} else if (GRANTTYPE_PASSWORD.equals(grant_type)) {
			msg.put(MessageProperties.SOAP_REQUEST_METHOD, "Resource Owner Password Credentials");
		} else {
			msg.put(MessageProperties.SOAP_REQUEST_METHOD, "Extended Grant Type");
		}

		try {
			/*
			 * parse client_id/secret, client_assertion and authenticate client according to
			 * provided credentials
			 */
			ApplicationDetails details = parseAuthenticationParameters(circuit, msg, headers, request, info, parsed, body, merged);

			if (enableGrantTypeFilter(msg) && (!isGrantTypeAllowed(details, grant_type))) {
				/* apply the grant filter if any */
				throw new OAuthException(err_rfc6749_unauthorized_client, null, "this client is not allowed to use the requested grant_type");
			}

			Set<String> remaining = new HashSet<String>(parsed.getDescriptors().keySet());
			RuntimeException saved = null;

			remaining.removeAll(parsed.getParsedParameters());

			for (String parameter : remaining) {
				try {
					parsed.parse(parameter, body, merged, null);
				} catch (RuntimeException e) {
					if (saved == null) {
						saved = e;
					}
				}
			}

			if (saved != null) {
				throw saved;
			}

			OAuthAccessTokenGenerator generator = getOAuthAccessTokenGenerator();
			Set<String> additionalScopes = new LinkedHashSet<String>();

			msg.put("oauth.scopes.additional", additionalScopes); /* scope which needs consent without storage in access token */

			/*
			 * All parameters should now be parsed and categorized, apply flow specific
			 * part.
			 */
			if (GRANTTYPE_CODE.equals(grant_type)) {
				AuthorizationCode code = checkAuthorizationCode(msg, generator, parsed, details);

				return generator.createAccessToken(circuit, msg, parsed, details, code, additionalScopes);
			} else if (GRANTTYPE_CLIENT.equals(grant_type)) {
				if ((!allowPublicClientCredentials(msg)) && (!"confidential".equalsIgnoreCase(details.getClientType()))) {
					throw new OAuthException(err_rfc6749_invalid_grant, null, "only confidential clients can use client_credentials grant (RFC6749 section 4.4)");
				}

				Set<String> scopes = getScopesForToken(msg, circuit, parsed, details, generator, null, additionalScopes);

				return generator.createAccessToken(circuit, msg, parsed, details, null, scopes, additionalScopes, false);
			} else if (GRANTTYPE_JWT.equals(grant_type) || GRANTTYPE_SAML2.equals(grant_type) || GRANTTYPE_PASSWORD.equals(grant_type)) {
				PolicyResource authenticator = getGrantAuthenticatorCircuit();

				if ((!isValidPolicy(authenticator))) {
					throw new OAuthException(err_rfc6749_unsupported_grant_type, null, String.format("grant type '%s' is not supported", grant_type));
				}

				if (!invokePolicy(msg, circuit, authenticator)) {
					/* trace error, but report invalid credentails */
					Trace.error("Grant Authenticator returned false");

					throw new OAuthException(err_rfc6749_invalid_grant, null, "provided grant is not valid");
				}

				String subject = AUTHENTICATED_SUBJECT.substitute(msg);

				if ((subject != null) && (subject.length() == 0)) {
					subject = null;
				}

				if (subject == null) {
					/* trace error, but report invalid credentails */
					Trace.error("Grant Authenticator did not return a valid subject");

					throw new OAuthException(err_rfc6749_invalid_grant, null, "provided grant is not valid");
				}

				Set<String> scopes = getScopesForToken(msg, circuit, parsed, details, generator, subject, additionalScopes);

				return generator.createAccessToken(circuit, msg, parsed, details, subject, scopes, additionalScopes, false);
			} else if (GRANTTYPE_TOKEN.equals(grant_type)) {
				String subject_token = (String) parsed.get(param_subject_token);

				if (subject_token == null) {
					throw new OAuthException(err_invalid_request, null, "subject_token parameter is required for token exchange flow");
				}

				String subject_token_type = (String) parsed.get(param_subject_token_type);

				if (subject_token_type == null) {
					throw new OAuthException(err_invalid_request, null, "subject_token_type parameter is required for token exchange flow");
				}

				OAuth2AccessToken subject = validateToken(msg, generator, param_subject_token, subject_token, subject_token_type);
				OAuth2AccessToken actor = null;
				String subjectName = null;

				boolean authenticate = false;

				if (subject != null) {
					subjectName = subject.getAuthenticationSubject();
					
					if ((subjectName == null) || subjectName.isEmpty()) {
						subjectName = null;
					}
					
					msg.put(MessageProperties.AUTHN_SUBJECT_ID, subjectName);
					msg.put("oauth.request.subject.token", subject);
					
					TokenStore store = generator.getTokenStore();
					OAuth2Authentication authn = store.readAuthenticationFromToken(subject);
					AuthorizationRequest authz = authn.getAuthorizationRequest();
					
					if (authn.isClientOnly() || (authz.getClientId().equals(subject.getClientID()))) {
						/* special case for client_credentials */
						subjectName = null;
					}
				} else {
					authenticate |= true;
				}

				String actor_token = (String) parsed.get(param_actor_token);

				if (actor_token != null) {
					String actor_token_type = (String) parsed.get(param_actor_token_type);

					if (actor_token_type == null) {
						throw new OAuthException(err_invalid_request, null, "actor_token_type parameter is required when actor_token is provided");
					}

					actor = validateToken(msg, generator, param_actor_token, actor_token, actor_token_type);

					if (actor != null) {
						msg.put("oauth.request.actor.token", actor);
					}
				}

				if (authenticate) {
					PolicyResource authenticator = getGrantAuthenticatorCircuit();

					if ((!isValidPolicy(authenticator))) {
						throw new OAuthException(err_rfc6749_unsupported_grant_type, null, String.format("grant type '%s' is not supported", grant_type));
					}

					if (!invokePolicy(msg, circuit, authenticator)) {
						/* trace error, but report invalid credentails */
						Trace.error("Grant Authenticator returned false");

						throw new OAuthException(err_rfc6749_invalid_grant, null, "provided grant is not valid");
					}

					subjectName = AUTHENTICATED_SUBJECT.substitute(msg);

					if ((subjectName != null) && (subjectName.length() == 0)) {
						subjectName = null;
					}

					if (subjectName == null) {
						/* trace error, but report invalid credentails */
						Trace.error("Grant Authenticator did not return a valid subject");

						throw new OAuthException(err_rfc6749_invalid_grant, null, "provided grant is not valid");
					}
				}

				String requested_token_type = (String) parsed.get(param_requested_token_type);
				Set<String> scopes = getScopesForToken(msg, circuit, parsed, details, generator, subjectName, additionalScopes);

				return generator.createAccessToken(circuit, msg, parsed, details, subjectName, scopes, additionalScopes, uri_token_type_refresh_token.equals(requested_token_type));
			} else if (GRANTTYPE_REFRESH.equals(grant_type)) {
				String refresh_token = (String) parsed.get(param_refresh_token);

				if (refresh_token == null) {
					throw new OAuthException(err_invalid_request, null, "refresh_token parameter is required for refresh token flow (RFC6749 section 6)");
				}

				TokenStore store = generator.getTokenStore();
				OAuth2RefreshToken token = store.readRefreshToken(refresh_token);

				if (token == null) {
					Trace.error(String.format("invalid refresh token supplied: %s", refresh_token));

					throw new OAuthException(err_rfc6749_invalid_grant, null, "provided grant is not valid");
				}

				if (token.hasExpired()) {
					Trace.error("the refresh token has expired");

					throw new OAuthException(err_rfc6749_invalid_grant, null, "provided grant is not valid");
				}

				OAuth2Authentication authn = store.readAuthenticationForRefreshToken(refresh_token);

				if (authn == null) {
					Trace.error(String.format("no recorded authentication for refresh token %s", refresh_token));

					throw new OAuthException(err_rfc6749_invalid_grant, null, "provided grant is not valid");
				}

				AuthorizationRequest authz = authn.getAuthorizationRequest();

				if (authz == null) {
					Trace.error(String.format("no recorded authorization for refresh token %s", refresh_token));

					throw new OAuthException(err_rfc6749_invalid_grant, null, "provided grant is not valid");
				}

				if (!authz.getClientId().equals(parsed.get(param_client_id))) {
					Trace.error(String.format("refresh token client_id mismatch (stored %s)", authz.getClientId()));

					throw new OAuthException(err_rfc6749_invalid_grant, null, "provided grant is not valid");
				}

				Set<String> scopes = new ScopeSet(parsed.getObjectNode());
				String subjectName = null;

				if (scopes.isEmpty()) {
					scopes.addAll(authz.getScope());
				} else if (!authz.getScope().containsAll(scopes)) {
					throw new OAuthException(err_rfc6749_invalid_scope, null, "requested scope MUST not include any scope not originally granted by the resource owner (RFC6749 section 6)");
				}

				msg.put(MessageProperties.AUTHN_SUBJECT_ID, subjectName = authn.getUserAuthentication());

				return generator.createAccessToken(circuit, msg, parsed, details, subjectName, scopes, additionalScopes, token);
			} else {
				PolicyResource decoder = getGrantDecoderCircuit();

				if ((!isValidPolicy(decoder))) {
					throw new OAuthException(err_rfc6749_unsupported_grant_type, null, String.format("grant type '%s' is not supported", grant_type));
				}

				if (!invokePolicy(msg, circuit, decoder)) {
					/* trace error, but report invalid credentails */
					Trace.error("Grant Decoder returned false");

					throw new OAuthException(err_rfc6749_invalid_grant, null, "provided grant is not valid");
				}

				PolicyResource authenticator = getGrantAuthenticatorCircuit();

				if ((!isValidPolicy(authenticator))) {
					throw new OAuthException(err_rfc6749_unsupported_grant_type, null, String.format("grant type '%s' is not supported", grant_type));
				}

				if (!invokePolicy(msg, circuit, authenticator)) {
					/* trace error, but report invalid credentails */
					Trace.error("Grant Authenticator returned false");

					throw new OAuthException(err_rfc6749_invalid_grant, null, "provided grant is not valid");
				}

				String subject = AUTHENTICATED_SUBJECT.substitute(msg);

				if ((subject != null) && (subject.length() == 0)) {
					subject = null;
				}

				if (subject == null) {
					/* trace error, but report invalid credentails */
					Trace.error("Grant Authenticator did not return a valid subject");

					throw new OAuthException(err_rfc6749_invalid_grant, null, "provided grant is not valid");
				}

				Set<String> scopes = getScopesForToken(msg, circuit, parsed, details, generator, subject, additionalScopes);

				return generator.createAccessToken(circuit, msg, parsed, details, subject, scopes, additionalScopes, false);
			}
		} catch (WebApplicationException e) {
			throw e;
		} catch (CircuitAbortException e) {
			Trace.error("Unexpected error", e);

			throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, "unexpected error occured", e);
		} catch (RuntimeException e) {
			Trace.error("Unexpected error", e);

			throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, "unexpected error occured", e);
		}
	}

	private static final Set<String> INTERNAL_TOKENTYPES = internalTokenTypes();

	private static Set<String> internalTokenTypes() {
		Set<String> tokenTypes = new HashSet<String>();

		tokenTypes.add(uri_token_type_access_token);
		tokenTypes.add(uri_token_type_refresh_token);

		return Collections.unmodifiableSet(tokenTypes);
	}

	private static OAuth2AccessToken validateToken(Message msg, OAuthAccessTokenGenerator generator, String subject_param, String subject_token, String subject_token_type) {
		OAuth2AccessToken token = null;

		if (INTERNAL_TOKENTYPES.contains(subject_token_type)) {
			if (uri_token_type_access_token.equals(subject_token_type)) {
				TokenStore store = generator.getTokenStore();

				token = store.readAccessToken(subject_token);

				if ((token == null) || token.isExpired()) {
					Trace.error(String.format("invalid %s (no access token)", subject_param));

					throw new OAuthException(err_rfc6749_invalid_grant, null, String.format("%s parameter is invalid", subject_param));
				}
			} else if (uri_token_type_refresh_token.equals(subject_token_type)) {
				TokenStore store = generator.getTokenStore();
				OAuth2RefreshToken refresh_token = store.readRefreshToken(subject_token);

				if ((refresh_token == null) || refresh_token.hasExpired()) {
					Trace.error(String.format("invalid %s (no refresh token)", subject_param));

					throw new OAuthException(err_rfc6749_invalid_grant, null, String.format("%s parameter is invalid", subject_param));
				}

				token = generator.generateToken(msg, refresh_token);
			} else {
				throw new OAuthException(err_rfc6749_invalid_grant, null, String.format("requested token type for %s is unknown", subject_param));
			}
		}

		return token;
	}

	protected AuthorizationCode checkAuthorizationCode(Message msg, OAuthAccessTokenGenerator generator, OAuthParameters parsed, ApplicationDetails details) {
		String code_parameter = (String) parsed.get(param_code);

		if (code_parameter == null) {
			throw new OAuthException(err_invalid_request, null, "code parameter is required for authorization code flow");
		}

		AuthorizationCodeStore store = generator.getAuthorizationCodeStore();

		if (store == null) {
			Trace.error("Authorization code store is not configured ");

			throw new OAuthException(err_rfc6749_unsupported_grant_type, null, "the provided grant_type is not supported");
		}

		AuthorizationCode code = store.get(code_parameter);

		if (code == null) {
			Trace.error("Provided code does not exists");
			
			/* XXX original filter discards existing access tokens obtained with this authorization code */

			throw new OAuthException(Response.Status.FORBIDDEN, err_rfc6749_invalid_grant, null, "the provided authorization_code is not valid or has expired");
		}

		if (code.hasExpired()) {
			Trace.error("Provided code has expired");

			throw new OAuthException(Response.Status.FORBIDDEN, err_rfc6749_invalid_grant, null, "the provided authorization_code has expired");
		}
		
		String provided_uri = getRedirectURI((String) parsed.get(param_redirect_uri));
		String code_uri= getRedirectURI(code.getRedirectURI());

		if (provided_uri == null ? code_uri != null : !provided_uri.equals(code_uri)) {
			Trace.error("redirect_uri mismatch");

			throw new OAuthException(Response.Status.FORBIDDEN, err_rfc6749_invalid_grant, null, "the provided redirect_uri is not valid for this authorization code");
		}

		Map<String, String> extensions = code.getAdditionalInformation();
		String code_challenge = extensions.get(param_code_challenge);
		String code_challenge_method = extensions.get(param_code_challenge_method);

		checkCodeVerifier(code_challenge, code_challenge_method, parsed);

		if (!details.getClientID().equals(code.getApplicationName())) {
			throw new OAuthException(Response.Status.FORBIDDEN, err_rfc6749_invalid_client, null, "the provided client_id is not authorized to use this authorization code");
		}
		
		if (!store.remove(code)) {
			Trace.error(String.format("Unable to remove authorization code '%s' from underlying store", code.getCode()));

			throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, "an internal error occured");
		}
		
		msg.put("authzcode", code);

		return code;
	}

	public static String getRedirectURI(String redirect_uri) {
		if (redirect_uri != null) {
			try {
				URI uri = new URI(redirect_uri);
				
				if (!uri.isOpaque()) {
					uri = uri.normalize();
				}
				
				redirect_uri = uri.toString();
			} catch (URISyntaxException e) {
			}
		}
		
		return redirect_uri;
	}

	protected static void checkCodeVerifier(String code_challenge, String code_challenge_method, OAuthParameters parsed) {
		if ((code_challenge != null) && (code_challenge_method != null)) {
			String code_verifier = (String) parsed.get(param_code_verifier);
			boolean valid = false;

			if (code_verifier == null) {
				throw new OAuthException(Response.Status.FORBIDDEN, err_rfc6749_invalid_grant, null, "the request is missing the code_verifier parameter");
			}

			/* check the code verifier */
			if ("plain".equals(code_challenge_method)) {
				valid = code_verifier.equals(code_challenge);
			} else if ("S256".equals(code_challenge_method)) {
				try {
					MessageDigest digester = MessageDigest.getInstance("SHA-256");

					digester.update(code_verifier.getBytes(ISO8859));

					byte[] challenge = new Base64URL(code_challenge).decode();
					byte[] digest = digester.digest();

					valid = (challenge != null) && (digest != null) && Arrays.equals(challenge, digest);
				} catch (NoSuchAlgorithmException e) {
					throw new OAuthException(err_rfc6749_unsupported_grant_type, null, "the provided grant_type is not supported", e);
				}
			}

			if (!valid) {
				throw new OAuthException(Response.Status.FORBIDDEN, err_rfc6749_invalid_grant, null, "code_verifier parameter is invalid");
			}
		}
	}
}
