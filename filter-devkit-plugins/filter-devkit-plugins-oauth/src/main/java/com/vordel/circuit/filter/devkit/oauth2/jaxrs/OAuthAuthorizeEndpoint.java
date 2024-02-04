package com.vordel.circuit.filter.devkit.oauth2.jaxrs;

import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.err_invalid_request;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.err_invalid_request_object;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.err_invalid_request_uri;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.err_request_not_supported;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.err_request_uri_not_supported;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.err_rfc6749_access_denied;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.err_rfc6749_invalid_scope;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.err_rfc6749_server_error;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.err_rfc6749_temporarily_unavailable;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.err_rfc6749_unauthorized_client;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_claims;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_claims_locales;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_client_id;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_code;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_code_challenge;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_code_challenge_method;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_display;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_id_token;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_id_token_hint;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_login_hint;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_max_age;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_nonce;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_prompt;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_redirect_uri;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_registration;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_request;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_request_uri;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_resource;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_response_mode;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_response_type;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_scope;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_state;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_ui_locales;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.response_mode_fragment;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.response_mode_query;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.response_type_code;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.response_type_code_id_token;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.response_type_code_id_token_token;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.response_type_code_token;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.response_type_id_token;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.response_type_id_token_token;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.response_type_none;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.response_type_token;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.POST;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JOSEObject;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.filter.devkit.context.resources.PolicyResource;
import com.vordel.circuit.filter.devkit.context.resources.SelectorResource;
import com.vordel.circuit.filter.devkit.jaxrs.MultivaluedHeaderMap;
import com.vordel.circuit.filter.devkit.oauth2.OAuthConsentManager;
import com.vordel.circuit.filter.devkit.oauth2.OAuthGuavaCache;
import com.vordel.circuit.filter.devkit.oauth2.model.OAuthParameters;
import com.vordel.circuit.filter.devkit.oauth2.model.PromptSet;
import com.vordel.circuit.filter.devkit.oauth2.model.ResponseTypeSet;
import com.vordel.circuit.filter.devkit.oauth2.model.ScopeSet;
import com.vordel.circuit.filter.devkit.oauth2.runtime.TokenServiceFilter.OAuthTokenData;
import com.vordel.circuit.oauth.kps.ApplicationDetails;
import com.vordel.circuit.oauth.provider.AuthorizationRequest;
import com.vordel.circuit.oauth.store.AuthorizationCodeStore;
import com.vordel.circuit.oauth.store.TokenStore;
import com.vordel.circuit.oauth.token.AuthorizationCode;
import com.vordel.circuit.oauth.token.OAuth2AccessToken;
import com.vordel.circuit.oauth.token.OAuth2Authentication;
import com.vordel.config.Circuit;
import com.vordel.el.Selector;
import com.vordel.mime.Body;
import com.vordel.mime.FormURLEncodedBody;
import com.vordel.mime.QueryStringHeaderSet;
import com.vordel.trace.Trace;

public abstract class OAuthAuthorizeEndpoint extends OAuthServiceEndpoint {
	public static final String OUT_OF_BAND_URL = "urn:ietf:wg:oauth:2.0:oob";

	private static final Selector<String> BODY_SELECTOR = SelectorResource.fromExpression("content.body", String.class);

	protected abstract boolean enableJsonPOST(Message msg);

	protected abstract boolean enableResponseTypeFilter(Message msg);

	protected abstract boolean forcePKCE(Message msg);

	protected abstract boolean skipUserConsent(Message msg);

	protected abstract PolicyResource getRequestRetriever();

	protected abstract PolicyResource getRequestValidator();

	protected abstract PolicyResource getRedirectGenerator();

	/**
	 * 
	 * @return resource owner authenticator policy
	 */
	protected abstract PolicyResource getAuthenticationPolicy();

	protected abstract PolicyResource getAuthorizationPolicy();

	protected abstract PolicyResource getPublicResourceOwnerGenerator();

	protected abstract String getPublicResourceOwner(Message msg);

	protected abstract Set<?> getPersistentAllowedScopes(Message msg);

	protected abstract Set<?> getTransientAllowedScopes(Message msg);

	protected abstract Set<?> getDiscardedScopes(Message msg);

	protected abstract int getAuthorizationCodeLength(Message msg);

	protected abstract long getAuthorizationCodeExpiration(Message msg);

	protected abstract OAuthAccessTokenGenerator getOAuthAccessTokenGenerator();

	@GET
	public final Response serviceGET(@Context Circuit circuit, @Context Message msg, @Context HttpHeaders headers, @Context Request request, @Context UriInfo info) {
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
		Set<String> response_types = new ResponseTypeSet(parsed.getObjectNode());
		Set<String> scopes = new ScopeSet(parsed.getObjectNode());

		/* save http client headers */
		msg.put("oauth.request.parsed.headers", msg.get(MessageProperties.HTTP_HEADERS));

		/* just in case some inner scripts needs this */
		msg.put("oauth.jaxrs.headers", headers);
		msg.put("oauth.jaxrs.request", request);
		msg.put("oauth.jaxrs.uri_info", info);
		
		/* set oauth request parsed parameters */
		msg.put("oauth.request.uri", getServiceURI(info));
		msg.put("oauth.request.parsed.mapped", parsed);
		msg.put("oauth.request.parsed.scopes", scopes);
		msg.put("oauth.request.parsed.prompts", new PromptSet(parsed.getObjectNode()));
		msg.put("oauth.request.parsed.response_type", response_types);
		msg.put("oauth.request.parsed.json", parsed.getObjectNode());
		msg.put("oauth.request.extras.form", merged);
		msg.put("oauth.request.extras.json", body);

		/* may be needed for content negociation */
		msg.put("oauth.request.jaxrs.request", body);

		/* register authorize parameters */
		OAuthParameters.register(parsed, param_client_id);
		OAuthParameters.register(parsed, param_response_type);
		OAuthParameters.register(parsed, param_redirect_uri);
		OAuthParameters.register(parsed, param_scope);
		OAuthParameters.register(parsed, param_state);
		OAuthParameters.register(parsed, param_nonce);
		OAuthParameters.register(parsed, param_display);
		OAuthParameters.register(parsed, param_prompt);
		OAuthParameters.register(parsed, param_max_age);
		OAuthParameters.register(parsed, param_ui_locales);
		OAuthParameters.register(parsed, param_claims_locales);
		OAuthParameters.register(parsed, param_id_token_hint);
		OAuthParameters.register(parsed, param_login_hint);
		OAuthParameters.register(parsed, param_claims);
		OAuthParameters.register(parsed, param_registration);
		OAuthParameters.register(parsed, param_request);
		OAuthParameters.register(parsed, param_request_uri);
		OAuthParameters.register(parsed, param_code_challenge);
		OAuthParameters.register(parsed, param_code_challenge_method);
		OAuthParameters.register(parsed, param_resource);
		OAuthParameters.register(parsed, param_response_mode);

		/* XXX missing vtr and acr_values */

		/* try to retrieve client_id from query */
		String client_id = parsed.parse(param_client_id, body, merged, null).asText(null);
		ApplicationDetails details = client_id == null ? null : OAuthGuavaCache.getAppDetailsFromClientId(msg, client_id);

		/* early parsing for redirect_uri for error reporting */
		parsed.parse(param_redirect_uri, body, merged, null);

		try {
			try {
				/*
				 * now that we know where to send response, parse the very last response
				 * parameter
				 */
				parsed.parse(param_response_mode, body, merged, null);

				String response_type = parsed.parse(param_response_type, body, merged, null).asText(null);

				/* try to set the soap method in traffic monitor */
				setSoapMethod(msg, response_type);

				/* retrieve and validate request object */
				ObjectNode override = parseOpenIDRequest(msg, circuit, parsed, body, merged);

				/* parse remaining body and query parameters */
				parseRemainingParameters(parsed, body, merged);

				/* apply request override */
				applyRequestOverride(parsed, body, override);

				ObjectNode claims = parsed.getObjectNode();
				String resource = claims.path(param_resource).asText(null);
				String nonce = claims.path(param_nonce).asText(null);

				if (client_id == null) {
					client_id = claims.path(param_client_id).asText(null);

					if (client_id == null) {
						throw new OAuthException(err_invalid_request, null, "the OAuth request is missing the client_id parameter");
					}

					details = OAuthGuavaCache.getAppDetailsFromClientId(msg, client_id);
				}

				if (response_type == null) {
					/* if traffic monitor method is not set yet, do it now */
					response_type = claims.path(param_response_type).asText(null);

					/*
					 * adjust the soap method (just in case response_type was not provided in query
					 * or body)
					 */
					setSoapMethod(msg, response_type);

					if (response_type == null) {
						throw new OAuthException(err_invalid_request, null, "the OAuth request is missing the response_type parameter");
					}
				}

				if (details == null) {
					/* trace error, but report invalid credentails */
					Trace.error(String.format("no registered client for client_id '%s'", client_id));

					throw new OAuthException(Response.Status.FORBIDDEN, err_rfc6749_unauthorized_client, null, "The client is not authorized use this method ");
				}

				msg.put("oauth.request.client_id", details.getClientID());

				if (getValidRedirectURI(details, claims.path(param_redirect_uri).asText(null)) == null) {
					throw new OAuthException(err_invalid_request, null, "invalid redirect_uri parameter");
				}

				/* check if 'nonce' parameter is required */
				if (response_type_code.equals(response_type) || response_type_token.equals(response_type)) {
					/* regular OAuth request without id token, nothing todo */
				} else if ((!response_type_none.equals(response_type)) && (nonce == null)) {
					throw new OAuthException(err_invalid_request, null, "the OAuth request is missing the nonce parameter (OpenID Connect Core sections 3.2.2.10 and 3.3.2.11)");
				}

				/* check if the given OAuth flow is enabled */
				if (enableResponseTypeFilter(msg) && (!isResponseTypeAllowed(details, response_types))) {
					throw new OAuthException(Response.Status.FORBIDDEN, err_rfc6749_unauthorized_client, null, "The client is not authorized use this method ");
				}

				String code_challenge = claims.path(param_code_challenge).asText(null);
				String code_challenge_method = claims.path(param_code_challenge_method).asText(null);

				/* check if PKCE usage is mandatory */
				if (response_types.contains(response_type_code) && forcePKCE(msg) && ((code_challenge == null) || (code_challenge_method == null))) {
					throw new OAuthException(Response.Status.FORBIDDEN, err_rfc6749_unauthorized_client, null, "usage of PKCE is mandatory");
				}

				OAuthAccessTokenGenerator tokenGenerator = getOAuthAccessTokenGenerator();
				Set<String> requestedScopes = tokenGenerator.getScopesForToken(msg, circuit, parsed, details);
				Set<String> additionalScopes = new LinkedHashSet<String>();

				msg.put("oauth.scopes.requested", requestedScopes);
				msg.put("oauth.scopes.additional", additionalScopes); /* scope which needs consent without storage in access token */

				/* 
				 * first step, check for user authentication. the policy have to implement all the needed logic for user authentication:
				 * - check for incoming authentication (id_token_hint),
				 * - apply prompt parameter semantics (passive authentication, interraction, etc...)
				 * 
				 * as a result, the policy must return an id token for internal usage only.
				 */

				PolicyResource authenticator = getAuthenticationPolicy();
				String subject = null;

				if (!isValidPolicy(authenticator)) {
					throw new OAuthException(Response.Status.SERVICE_UNAVAILABLE, err_rfc6749_temporarily_unavailable, null, "the server is not able to authenticate resource owner");
				}

				if (invokePolicy(msg, circuit, authenticator)) {
					subject = AUTHENTICATED_SUBJECT.substitute(msg);

					if ((subject != null) && (subject.length() == 0)) {
						subject = null;
					}

					if (subject == null) {
						throw new OAuthException(Response.Status.FORBIDDEN, err_rfc6749_access_denied, null, "unable to authenticate resource owner");
					}

					/* We now have a valid authenticated user, check for resource owner consent */
				} else {
					/*
					 * the policy returned false, this means that we have a reliable response to
					 * return to the resource owner (identity provider redirection, login form,
					 * etc...)
					 */
					return toResponse(msg);
				}

				/*
				 * second step, process user optional consent
				 */
				OAuthConsentManager manager = new OAuthConsentManager(tokenGenerator.getTokenStore(), skipUserConsent(msg));
				Set<String> fullRequestedScopes = new LinkedHashSet<String>();

				fullRequestedScopes.addAll(requestedScopes);
				fullRequestedScopes.addAll(additionalScopes);

				if (manager.scopesNeedOwnerAuthorization(msg, subject, details, fullRequestedScopes)) {
					PolicyResource authorization = getAuthorizationPolicy();

					/* 
					 * Those two sets generic type are strings but since selector API does not support
					 * generics, use wildcard for now.
					 */
					Set<?> persistentAllowedScopes = null;
					Set<?> transientAllowedScopes = null;
					Set<?> discardedScopes = null;

					if (!isValidPolicy(authorization)) {
						throw new OAuthException(err_rfc6749_invalid_scope, null, "requested scopes need user consent");
					}

					msg.put("oauth.scopes.missing", manager.getScopesForAuthorisation());

					if (invokePolicy(msg, circuit, authorization)) {
						persistentAllowedScopes = getPersistentAllowedScopes(msg);
						transientAllowedScopes = getTransientAllowedScopes(msg);
						discardedScopes = getDiscardedScopes(msg);
					} else {
						/*
						 * the policy returned false, this means that we have a reliable response to
						 * return to the resource owner (identity provider redirection, login form,
						 * etc...)
						 */
						return toResponse(msg);
					}

					if (persistentAllowedScopes == null) {
						persistentAllowedScopes = Collections.emptySet();
					}

					if (transientAllowedScopes == null) {
						transientAllowedScopes = Collections.emptySet();
					}

					if (discardedScopes != null) {
						requestedScopes.removeAll(discardedScopes);
						additionalScopes.removeAll(discardedScopes);
						persistentAllowedScopes.removeAll(discardedScopes);
						transientAllowedScopes.removeAll(discardedScopes);
					}

					fullRequestedScopes.clear();
					fullRequestedScopes.addAll(requestedScopes);
					fullRequestedScopes.addAll(additionalScopes);

					if (manager.scopesNeedOwnerAuthorization(msg, subject, details, fullRequestedScopes, persistentAllowedScopes, transientAllowedScopes)) {
						/* If we still have scopes to approve, return invalid scope error */
						throw new OAuthException(err_rfc6749_invalid_scope, null, "requested scopes need user consent");
					}
				}

				CacheControl cache = CacheControl.valueOf("no-store");
				ObjectNode result = MAPPER.createObjectNode();

				/* modify incoming request to reflect real requested scopes */
				scopes.retainAll(requestedScopes);
				scopes.addAll(requestedScopes);
				
				if (!response_type_none.equals(response_type)) {
					AuthorizationRequest authz = new AuthorizationRequest(parsed.toQueryString(new QueryStringHeaderSet()));
					OAuth2Authentication authn = new OAuth2Authentication(authz, subject);
					OAuth2AccessToken token = null;
					AuthorizationCode code = null;
					String id_token = null;

					PolicyResource idGenerator = getPublicResourceOwnerGenerator();
					Map<String, String> additional = new HashMap<String, String>();
					
					msg.put("oauth.authorization.request", authz); /* compatibility with existing filter */

					for (OAuthTokenData entry : tokenGenerator.getAdditionalData()) {
						String key = entry.getKey(msg);

						if (key != null) {
							if (!OAuthAccessTokenGenerator.RESERVED_INFORMATION.contains(key)) {
								String value = entry.getValue(msg);

								if (value != null) {
									additional.put(key, value);
								}
							}
						}
					}

					additional.keySet().removeAll(OAuthAccessTokenGenerator.RESERVED_INFORMATION);
					additional.put("oauth.scopes.additional", MAPPER.writeValueAsString(OAuthAccessTokenGenerator.toJsonArray(additionalScopes)));

					if (nonce != null) {
						additional.put("internalstorage.openid.nonce", nonce);
					}

					if ((code_challenge != null) && (code_challenge_method != null)) {
						additional.put(param_code_challenge, code_challenge);
						additional.put(param_code_challenge_method, code_challenge_method);
					}

					if (resource != null) {
						additional.put(param_resource, resource);
					}

					/*
					 * generate requested tokens and put them in the resulting json object
					 */
					if (response_types.contains(response_type_code)) {
						int length = getAuthorizationCodeLength(msg);
						long expiration = getAuthorizationCodeExpiration(msg);

						if (length <= 0) {
							Trace.error("authorization code length is too small");

							throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, null);
						}

						if (expiration <= 0L) {
							Trace.error("authorization code expiry is zero or negative");

							throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, null);
						}

						/* generate authorization code and save it in message */
						msg.put("authzcode", code = new AuthorizationCode(authz.getRedirectUri(), authz.getScope(), length, expiration, subject, authz.getState(), authz.getClientId(), additional));
					}

					if (response_types.contains(response_type_token)) {
						token = tokenGenerator.generateToken(msg, circuit, parsed, details, authz.getScope(), additionalScopes, additional, false);
						tokenGenerator.setTokenOnMessage(msg, token);

						/* existing filter compatibility */
						msg.put("accesstoken.authn", authn);

						token.setAuthenticationSubject(authn.getUserAuthentication());
						token.setClientID(authz.getClientId());
					}

					if ((code != null) || (token != null)) {
						PolicyResource transformer = tokenGenerator.getAccessTokenTransformer();

						if (isValidPolicy(transformer) && (!invokePolicy(msg, circuit, transformer))) {
							throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, "unable to generate token or authorization code");
						}
					}

					if (response_types.contains(response_type_id_token)) {
						if (!isValidPolicy(idGenerator)) {
							throw new OAuthException(err_invalid_request, null, "id_token generation is not supported");
						}

						if (!invokePolicy(msg, circuit, idGenerator)) {
							throw new OAuthException(err_invalid_request_uri, null, "unable to generate the id token");
						}

						JOSEObject jose = null;

						try {
							id_token = getPublicResourceOwner(msg);

							if (id_token != null) {
								jose = JOSEObject.parse(id_token);

								if (code != null) {
									code.setAdditionalInformation(param_id_token, id_token);
								}

								if (token != null) {
									/* if we did parse the ID Token set it for the generated token */
									token.setIdToken(id_token);
									token.setAdditionalInformation(param_id_token, id_token);
								}
							}

							msg.put("oauth.response.id_token", id_token);
						} catch (ParseException e) {
							Trace.error("unable to parse generated id token", e);
						}

						if (jose == null) {
							throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, "unable to generate the id token");
						}
					}

					if (code != null) {
						AuthorizationCodeStore codeStore = tokenGenerator.getAuthorizationCodeStore();

						if (!codeStore.add(code)) {
							throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, "unable to store authorization code");
						}

						result.put(param_code, code.getCode());
					}

					if (id_token != null) {
						result.put(param_id_token, id_token);
					}

					if (token != null) {
						TokenStore tokenStore = tokenGenerator.getTokenStore();
						
						if (token.hasRefresh()) {
							/* discard refresh token if any */
							token.setOAuth2RefreshToken(null);
						}

						if (!tokenStore.storeAccessToken(token, authn)) {
							throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, "unable to store access token");
						}

						ObjectNode node = (ObjectNode) MAPPER.readTree(token.getTokenAsJSON());

						/* ensure code and id_token are not set */
						node.remove(param_code);
						node.remove(param_id_token);

						result.setAll(OAuthAccessTokenGenerator.filterTokenAdditionalInformation(node, msg));
					}
				}

				return asOAuthRedirect(msg, circuit, parsed, Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(result).cacheControl(cache).build());
			} catch (CircuitAbortException e) {
				Trace.error("Got Exception from circuit", e);

				throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, "unexpected error occured", e);
			} catch (OAuthException e) {
				throw e;
			} catch (RuntimeException e) {
				Trace.error("Unexpected error", e);

				throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, "unexpected error occured", e);
			} catch (IOException e) {
				Trace.error("Unexpected error", e);

				throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, "unexpected error occured", e);
			}
		} catch (OAuthException e) {
			return asOAuthRedirect(msg, circuit, parsed, e.getResponse());
		}
	}

	public Response asOAuthRedirect(Message msg, Circuit circuit, OAuthParameters parsed, Response response) {
		/* try to retrieve the Json Response Entity */
		Object entity = getResponseJsonEntity(response);
		ObjectNode claims = parsed.getObjectNode();

		String client_id = claims.path(param_client_id).asText(null);
		ApplicationDetails details = client_id == null ? null : OAuthGuavaCache.getAppDetailsFromClientId(msg, client_id);
		String redirect_uri = getValidRedirectURI(details, claims.path(param_redirect_uri).asText(null));
		String response_mode = getResponseMode(parsed);

		/* ensure we have the minimum valid parameters to redirect user */
		if ((entity instanceof ObjectNode) && (details != null) && (redirect_uri != null) && (response_mode != null)) {
			try {
				FormURLEncodedBody form = asFormURLEncodedBody((ObjectNode) entity, claims.path(param_state).asText(null));
				URI location = null;

				/* check if we can produce the redirection */
				if ((response_mode_query.equals(response_mode) || response_mode_fragment.equals(response_mode)) && (!OUT_OF_BAND_URL.equals(redirect_uri))) {
					try {
						URI uri = new URI(redirect_uri).normalize();
						String scheme = uri.getScheme();
						String authority = uri.getAuthority();
						String path = uri.getPath();

						String rawQuery = uri.getRawQuery();
						String rawFragment = null;

						uri = createURI(scheme, authority, path, response_mode_query.equals(response_mode) ? rawQuery : null, null);

						UriBuilder uriBuilder = UriBuilder.fromUri(uri);
						QueryStringHeaderSet parameters = form.getParameters();
						Iterator<String> iterator = parameters.getHeaderNames();

						while (iterator.hasNext()) {
							String name = iterator.next();
							Iterator<String> values = parameters.getHeaders(name);

							while (values.hasNext()) {
								uriBuilder.queryParam(name, values.next());
							}
						}

						uri = uriBuilder.build();

						if (response_mode_fragment.equals(response_mode)) {
							rawFragment = uri.getRawQuery();
						} else {
							rawQuery = uri.getRawQuery();
						}

						location = createURI(scheme, authority, path, rawQuery, rawFragment);
					} catch (URISyntaxException e) {
						Trace.error("unable to generate redirect uri", e);
					}
				}

				if (location == null) {
					PolicyResource generator = getRedirectGenerator();

					if (isValidPolicy(generator)) {
						try {
							msg.put("oauth.response.parameters", form.getParameters());
							msg.put("oauth.response.mode", response_mode);
							msg.put("oauth.response.uri", redirect_uri);

							/* remove original headers and body */
							msg.remove(MessageProperties.HTTP_HEADERS);
							msg.remove(MessageProperties.CONTENT_BODY);

							if (invokePolicy(msg, circuit, generator)) {
								response = toResponse(msg);
							}
						} catch (CircuitAbortException e) {
							Trace.error("error generating redirect form", e);
						}
					}
				} else {
					ResponseBuilder responseBuilder = Response.status(302);

					response = responseBuilder.location(location).build();
				}
			} catch (IOException e) {
				Trace.error("unable to generate reponse parameters", e);
			}
		}

		return response;
	}

	private static FormURLEncodedBody asFormURLEncodedBody(ObjectNode node, String state) {
		FormURLEncodedBody form = FormURLEncodedBody.createBody();

		if (node != null) {
			try {
				Iterator<Entry<String, JsonNode>> iterator = node.fields();
				QueryStringHeaderSet parameters = form.getParameters();

				while (iterator.hasNext()) {
					Entry<String, JsonNode> fieldEntry = iterator.next();
					String fieldName = fieldEntry.getKey();

					if ((state == null) || (!param_state.equals(fieldName))) {
						JsonNode fieldNode = fieldEntry.getValue();

						if ((fieldNode != null) && (!fieldNode.isMissingNode()) && (!fieldNode.isNull())) {
							try {
								parameters.addHeader(fieldName, fieldNode.isTextual() ? fieldNode.asText(null) : MAPPER.writeValueAsString(fieldNode));
							} catch (JsonProcessingException e) {
								/* ignore */
							}
						}
					}
				}

				if (state != null) {
					parameters.addHeader(param_state, state);
				}
			} catch (IOException e) {
				/* XXX should not occur */
			}
		}

		return form;
	}

	private static URI createURI(String scheme, String authority, String path, String rawQuery, String rawFragment) throws URISyntaxException {
		URI result = new URI(scheme, authority, path, null, null);
		StringBuilder builder = new StringBuilder();

		builder.append(result.toASCIIString());

		if (rawQuery != null) {
			rawQuery = rawQuery.trim();
		}

		if (rawFragment != null) {
			rawFragment = rawFragment.trim();
		}

		if ((rawQuery != null) && (!rawQuery.isEmpty())) {
			builder.append('?');
			builder.append(rawQuery);
		}

		if ((rawFragment != null) && (!rawFragment.isEmpty())) {
			builder.append('#');
			builder.append(rawFragment);
		}

		return URI.create(builder.toString());
	}

	public static String getResponseMode(OAuthParameters parsed) {
		String response_mode = null;

		if (parsed != null) {
			ObjectNode claims = parsed.getObjectNode();

			response_mode = claims.path(param_response_mode).asText(null);

			if (response_mode == null) {
				String response_type = claims.path(param_response_type).asText(null);

				if (response_type_code.equals(response_type) || response_type_none.equals(response_type)) {
					response_mode = response_mode_query;
				} else {
					response_mode = response_mode_fragment;
				}
			}
		}

		return response_mode;
	}

	public static String getValidRedirectURI(ApplicationDetails details, String request_uri) {
		String result = null;

		if (details != null) {
			try {
				String requested = null;

				if (OUT_OF_BAND_URL.equals(request_uri)) {
					requested = request_uri;
				} else if (request_uri != null) {
					URI uri = new URI(request_uri);

					/* trim query and fragment from requested URI */
					requested = new URI(uri.getScheme(), uri.getAuthority().toLowerCase(), uri.getPath(), null, null).normalize().toString();
				}

				List<String> urls = details.getRedirectURLs();

				if (urls != null) {
					Iterator<String> iterator = urls.iterator();

					while ((result == null) && iterator.hasNext()) {
						try {
							String url = iterator.next();

							if (OUT_OF_BAND_URL.equals(url) && url.equals(requested)) {
								result = url;
							} else {
								URI uri = new URI(url);

								/* trim query and fragment from registered URI */
								String compare = new URI(uri.getScheme(), uri.getAuthority().toLowerCase(), uri.getPath(), null, null).normalize().toString();

								if ((requested == null) || requested.equals(compare)) {
									result = url;
								}
							}
						} catch (URISyntaxException e) {
							/* ignore invalid registered URIs */
						}
					}
				}
			} catch (URISyntaxException e) {
				/* ignore invalid redirect URI */
			}
		}

		return result;
	}

	private static void setSoapMethod(Message msg, String response_type) {
		if (response_type != null) {
			String method = null;

			if (response_type_none.equals(response_type)) {
				method = "OpenIDConnect None Request";
			} else if (response_type_code.equals(response_type)) {
				method = "Authorization Code Request";
			} else if (response_type_id_token.equals(response_type)) {
				method = "OpenIDConnect ID Token Request";
			} else if (response_type_token.equals(response_type)) {
				method = "Implicit Grant Token Request";
			} else if (response_type_code_id_token_token.equals(response_type)) {
				method = "OpenIDConnect Code, ID Token, and Token Request";
			} else if (response_type_code_id_token.equals(response_type)) {
				method = "OpenIDConnect Code and ID Token Request";
			} else if (response_type_code_token.equals(response_type)) {
				method = "OpenIDConnect Code and Token Request";
			} else if (response_type_id_token_token.equals(response_type)) {
				method = "OpenIDConnect ID Token and Token Request";
			} else {
				throw new OAuthException(err_invalid_request, null, "requested response_type is not supported");
			}

			msg.put(MessageProperties.SOAP_REQUEST_METHOD, method);
		}
	}

	private static void applyRequestOverride(OAuthParameters parsed, ObjectNode body, ObjectNode override) {
		if (override != null) {
			OAuthParameters request = new OAuthParameters(override, parsed.getDescriptors());
			ObjectNode extras = MAPPER.createObjectNode();

			extras.setAll(override);

			Iterator<String> iterator = request.keySet().iterator();

			/* remove all oauth parameters in extras object */
			while (iterator.hasNext()) {
				String name = iterator.next();

				extras.remove(name);
			}

			iterator = extras.fieldNames();

			/* remove extra parameters in overrides */
			while(iterator.hasNext()) {
				String name = iterator.next();

				override.remove(name);
			}

			/* parse overriden parameters */
			for(String name : new LinkedHashSet<String>(request.keySet())) {
				parsed.remove(name);
				parsed.getParsedParameters().remove(name);

				parsed.parse(name, override, null, null);
			}

			/* set extra parameters */
			body.setAll(extras);
		}
	}

	private static final void parseRemainingParameters(OAuthParameters parsed, ObjectNode body, MultivaluedMap<String, String> merged) {
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
	}

	private ObjectNode parseOpenIDRequest(Message msg, Circuit circuit, OAuthParameters parsed, ObjectNode body, MultivaluedMap<String, String> merged) throws CircuitAbortException {
		String request_uri = parsed.parse(param_request_uri, body, merged, null).asText(null);
		String request = parsed.parse(param_request, body, merged, null).asText(null);
		Body saved = (Body) msg.get(MessageProperties.CONTENT_BODY);
		ObjectNode override = null;

		if ((request_uri != null) && (request != null)) {
			throw new OAuthException(err_invalid_request, null, "request and request_uri are mutually exclusive (OpenID Connect Core section 6)");
		} else if (request_uri != null) {
			PolicyResource retriever = getRequestRetriever();

			if (!isValidPolicy(retriever)) {
				throw new OAuthException(err_request_uri_not_supported, null, "the request_uri parameter is not supported");
			}

			if (!invokePolicy(msg, circuit, retriever)) {
				throw new OAuthException(err_invalid_request_uri, null, "the request_uri parameter is invalid");
			}

			request = BODY_SELECTOR.substitute(msg);

			/* replace the request_uri parameter using the retrieved request */
			parsed.remove(param_request_uri);
			parsed.put(param_request, request);
		}

		if (request != null) {
			PolicyResource validator = getRequestValidator();

			if (!isValidPolicy(validator)) {
				throw new OAuthException(err_request_not_supported, null, "the request parameter is not supported");
			}

			if (!invokePolicy(msg, circuit, validator)) {
				throw new OAuthException(err_invalid_request_object, null, "the provided request object is invalid");
			}

			try {
				override = (ObjectNode) MAPPER.readTree(BODY_SELECTOR.substitute(msg));

				if (override.has(param_request) || override.has(param_request_uri)) {
					throw new OAuthException(err_invalid_request_object, null, "the provided request object cannot contain nested request or request_uri parameters (OpenID Connect Core section 6.1)");
				}

				ObjectNode claims = parsed.getObjectNode();
				JsonNode client_id = claims.get(param_client_id);
				JsonNode response_type = claims.get(param_response_type);

				if ((client_id != null) && (!client_id.equals(override.get(param_client_id)))) {
					throw new OAuthException(err_invalid_request_object, null, "the provided request object client_id is invalid (OpenID Connect Core section 6.1)");
				}

				if ((response_type != null) && (!response_type.equals(override.get(param_response_type)))) {
					throw new OAuthException(err_invalid_request_object, null, "the provided request object response_type is invalid (OpenID Connect Core section 6.1)");
				}

				claims.remove(param_request);
			} catch (IOException e) {
				throw new OAuthException(err_invalid_request_object, null, "the provided request object is invalid", e);
			} catch (ClassCastException e) {
				throw new OAuthException(err_invalid_request_object, null, "the provided request object is not a signed json object");
			}
		}

		if (saved != null) {
			/* restore the body object */
			msg.put(MessageProperties.CONTENT_BODY, saved);
		}


		return override;
	}
}
