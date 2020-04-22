package com.vordel.sdk.plugins.oauth2.jaxrs;

import static com.vordel.sdk.plugins.oauth2.jaxrs.OAuthTokenEndpoint.GRANTTYPE_TOKEN;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_invalid_request;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_rfc6749_invalid_grant;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_rfc6749_invalid_scope;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_rfc6749_server_error;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_access_token;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_audience;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_code_challenge;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_code_challenge_method;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_expires_in;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_grant_type;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_id_token;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_issued_token_type;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_refresh_token;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_requested_token_type;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_resource;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_scope;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_state;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_token_type;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.uri_token_type_access_token;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.uri_token_type_refresh_token;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.oauth.common.OAuthScopeUtils;
import com.vordel.circuit.oauth.common.OAuthScopeUtils.ScopesMustMatchSelection;
import com.vordel.circuit.oauth.kps.ApplicationDetails;
import com.vordel.circuit.oauth.provider.AuthorizationRequest;
import com.vordel.circuit.oauth.provider.CreateOAuthAccessTokenProcessor;
import com.vordel.circuit.oauth.store.AuthorizationCodeStore;
import com.vordel.circuit.oauth.store.TokenStore;
import com.vordel.circuit.oauth.token.AuthorizationCode;
import com.vordel.circuit.oauth.token.OAuth2AccessToken;
import com.vordel.circuit.oauth.token.OAuth2Authentication;
import com.vordel.circuit.oauth.token.OAuth2RefreshToken;
import com.vordel.circuit.script.context.resources.PolicyResource;
import com.vordel.common.apiserver.discovery.model.OAuthAppScope;
import com.vordel.config.Circuit;
import com.vordel.mime.QueryStringHeaderSet;
import com.vordel.sdk.plugins.oauth2.OAuthConsentManager;
import com.vordel.sdk.plugins.oauth2.OAuthGuavaCache;
import com.vordel.sdk.plugins.oauth2.filter.TokenServiceFilter.OAuthTokenData;
import com.vordel.sdk.plugins.oauth2.model.OAuthParameters;
import com.vordel.sdk.plugins.oauth2.model.ScopeSet;
import com.vordel.trace.Trace;

public abstract class OAuthAccessTokenGenerator {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	protected abstract Set<?> substituteCircuitScopes(Message msg);

	protected abstract ScopesMustMatchSelection getScopesMustMatchSelection(Message msg);

	protected abstract PolicyResource getScopeValidator();

	protected abstract String getScopesFrom(Message msg);

	protected abstract AuthorizationCodeStore getAuthorizationCodeStore();

	protected abstract TokenStore getTokenStore();

	protected abstract Collection<OAuthTokenData> getAdditionalData();

	protected abstract long getAccessTokenExpiration(Message msg);

	protected abstract int getAccessTokenLength(Message msg);

	protected abstract String getAccessTokenType(Message msg);

	protected abstract String getRefreshTokenChoice(Message msg);

	protected abstract String getPreserveChoice(Message msg);

	protected abstract long getRefreshTokenExpiration(Message msg);

	protected abstract int getRefreshTokenLength(Message msg);

	protected abstract boolean refreshRequireOfflineAccess(Message msg);

	protected abstract boolean allowRefreshToken(Message msg, ApplicationDetails details);

	protected abstract PolicyResource getGrantValidatorCircuit();

	protected abstract PolicyResource getAccessTokenTransformer();

	public static final Set<String> INTERNAL_INFORMATION;
	public static final Set<String> RESERVED_INFORMATION;

	static {
		Set<String> internal = new HashSet<String>();
		Set<String> reserved = new HashSet<String>();

		/* data generated from existing filters */
		internal.add("internalstorage.openid.nonce");

		/* persist PKCE related parameters */
		internal.add(param_code_challenge);
		internal.add(param_code_challenge_method);

		/* persist token exchange related parameters */
		reserved.add(param_resource);
		reserved.add(param_audience);

		/* persist id token if any */
		reserved.add(param_id_token);

		/* issued token type for token exchange */
		internal.add(param_issued_token_type);

		reserved.addAll(internal);

		INTERNAL_INFORMATION = Collections.unmodifiableSet(internal);
		RESERVED_INFORMATION = Collections.unmodifiableSet(reserved);
	}

	public Set<String> getScopesForToken(Message msg, Circuit circuit, OAuthParameters parsed, ApplicationDetails details) throws CircuitAbortException {
		String from = getScopesFrom(msg);
		Set<String> scopes = null;

		if ("Circuit".equals(from)) {
			PolicyResource validator = getScopeValidator();

			if (!OAuthServiceEndpoint.isValidPolicy(validator)) {
				Trace.error("Circuit scope validation is selected but no policy is configured for it");

				throw new OAuthException(err_rfc6749_invalid_scope, null, null);
			}

			scopes = getScopesForToken(msg, circuit, parsed, details, validator, null);
		} else if ("Application".equals(from)) {
			scopes = getScopesForToken(msg, circuit, parsed, details, null, getScopesMustMatchSelection(msg));
		} else {
			Trace.error("Bad configuration for scope validation");

			throw new OAuthException(err_rfc6749_invalid_scope, null, "unable to validate scopes");
		}

		return scopes;
	}

	protected Set<String> getScopesForToken(Message msg, Circuit circuit, OAuthParameters parsed, ApplicationDetails details, PolicyResource scopeCircuit, ScopesMustMatchSelection matchSelection) throws CircuitAbortException {
		/* create a scope set with parsed parameters as backend */
		Set<String> scopes = new ScopeSet(parsed.getObjectNode());

		if (matchSelection != null) {
			if (scopes.isEmpty()) {
				parsed.parse(param_scope, ScopeSet.asString(details.getDefaultScopes().iterator(), (scope) -> scope.getScope()), null);
			} else {
				Set<String> applicationScopes = new HashSet<String>();

				for (OAuthAppScope appScope : details.getScopes()) {
					applicationScopes.add(appScope.getScope());
				}

				applicationScopes.addAll(OAuthScopeUtils.retrieveOpenIDScopesIfInMessage(scopes));
				applicationScopes = OAuthScopeUtils.handleScopes(scopes, matchSelection, applicationScopes);

				scopes.retainAll(applicationScopes);
				scopes.addAll(applicationScopes);
			}
		}

		if (OAuthServiceEndpoint.isValidPolicy(scopeCircuit)) {
			if (!OAuthServiceEndpoint.invokePolicy(msg, circuit, scopeCircuit)) {
				throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_invalid_scope, null, "provided scopes are invalid");
			}

			Set<?> circuitScopes = substituteCircuitScopes(msg);

			if ((circuitScopes == null) || circuitScopes.isEmpty()) {
				scopes.clear();
			} else {
				parsed.parse(param_scope, ScopeSet.asString(circuitScopes.iterator(), (scope) -> scope instanceof String ? null : (String) scope), null);
			}
		}

		return scopes;
	}

	public Set<String> applyOwnerConsent(Message msg, String subject, ApplicationDetails details, Set<String> scopes, boolean skipUserConsent) throws CircuitAbortException {
		if (OAuthGuavaCache.getAppDetailsFromClientId(msg, subject) != null) {
			subject = null;
		}
		
		if (subject != null) {
			OAuthConsentManager manager = new OAuthConsentManager(getTokenStore(), skipUserConsent);

			if (manager.scopesNeedOwnerAuthorization(msg, subject, details, scopes)) {
				throw new OAuthException(err_rfc6749_invalid_scope, null, "requested scopes need user consent");
			}
		}

		return scopes;
	}

	public Response createAccessToken(Circuit circuit, Message msg, OAuthParameters parsed, ApplicationDetails details, String subject, Set<String> scopes, OAuth2RefreshToken refresh_token) throws CircuitAbortException {
		PolicyResource validator = getGrantValidatorCircuit();

		if (OAuthServiceEndpoint.isValidPolicy(validator) && (!OAuthServiceEndpoint.invokePolicy(msg, circuit, validator))) {
			/* trace error, but report invalid credentials */
			Trace.error("Grant Validator returned false");

			throw new OAuthException(err_rfc6749_invalid_grant, null, "provided grant is not valid");
		}

		String saved_refresh_token = refresh_token.getValue();
		OAuth2AccessToken token = generateToken(msg, refresh_token);
		String preserveChoice = getPreserveChoice(msg);
		boolean preserveRefresh = false;

		updateAdditionalInfo(msg, parsed, token);

		if ("Preserve".equals(preserveChoice)) {
			/* keep existing refresh token */
			preserveRefresh = true;
		} else {
			TokenStore tokenStore = getTokenStore();

			tokenStore.removeRefreshToken(refresh_token.getValue());
			generateRefreshtoken(msg, circuit, parsed, details, token, true);
			
			if ("Sliding".equals(preserveChoice)) {
				/* create a refresh_token with the same identifier */
				token.getOAuth2RefreshToken().setValue(saved_refresh_token);
			}
		}

		if (!storeToken(circuit, msg, parsed, setTokenOnMessage(msg, token), subject, preserveRefresh)) {
			Trace.error("unable to store Access Token to persistent store");

			throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, null);
		}

		return returnToken(token, null, saved_refresh_token, (String) parsed.get(param_grant_type));
	}


	public Response createAccessToken(Circuit circuit, Message msg, OAuthParameters parsed, ApplicationDetails details, AuthorizationCode code) throws CircuitAbortException {
		String subject = code.getUserIdentity();
		Set<String> scopes = code.getScopes();
		
		/* save the current authorization code in the message */
		msg.put("oauth.request.code", code);
		
		OAuth2AccessToken token = generateToken(msg, circuit, parsed, details, scopes, code.getAdditionalInformation(), false);

		if (!storeToken(circuit, msg, parsed, setTokenOnMessage(msg, token), subject, false)) {
			Trace.error("unable to store Access Token to persistent store");

			throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, null);
		}

		return returnToken(token, (String) parsed.get(param_state), null, (String) parsed.get(param_grant_type));
	}

	public Response createAccessToken(Circuit circuit, Message msg, OAuthParameters parsed, ApplicationDetails details, String subject, Set<String> scopes, boolean forceRefresh) throws CircuitAbortException {
		OAuth2AccessToken token = generateToken(msg, circuit, parsed, details, scopes, null, forceRefresh);

		if (!storeToken(circuit, msg, parsed, setTokenOnMessage(msg, token), subject, false)) {
			Trace.error("unable to store Access Token to persistent store");

			throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, null);
		}

		return returnToken(token, (String) parsed.get(param_state), null, (String) parsed.get(param_grant_type));
	}

	private Response returnToken(OAuth2AccessToken token, String state, String previous_refresh, String grant_type) {
		CacheControl cache = CacheControl.valueOf("no-store");
		ObjectNode node = null;

		try {
			Map<String, String> data = token.getAdditionalInformation();
			String issued_token_type = data.get(param_issued_token_type);

			/* ensure we are not generating internal data out of the response */
			data.keySet().removeAll(INTERNAL_INFORMATION);

			node = (ObjectNode) MAPPER.readTree(token.getTokenAsJSON());

			if ((issued_token_type == null) || issued_token_type.isEmpty() || uri_token_type_access_token.equals(issued_token_type)) {
				/* result is a regular access token */
				issued_token_type = uri_token_type_access_token;
				
				if (state != null) {
					/* add state claim if available */
					node.put(param_state, state);
				}
			} else {
				node.remove(param_refresh_token);
				node.put(param_token_type, "N_A");

				if (uri_token_type_refresh_token.equals(issued_token_type)) {
					OAuth2RefreshToken refresh_token = token.getOAuth2RefreshToken();

					if (refresh_token == null) {
						throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, "unable to generate requested token");
					}

					node.put(param_access_token, refresh_token.getValue());
					node.put(param_expires_in, refresh_token.getExpiresIn());
				} else {
					node.remove(param_expires_in);
				}
			}
			
			if ((previous_refresh != null) && previous_refresh.equals(node.path(param_refresh_token).asText(null))) {
				/* remove the refresh token if it has not been generated here */
				node.remove(param_refresh_token);
			}

			node.remove(INTERNAL_INFORMATION);
			
			if ((issued_token_type != null) && GRANTTYPE_TOKEN.equals(grant_type)) {
				node.put(param_issued_token_type, issued_token_type);
			}
		} catch (IOException e) {
			Trace.error("unable to generate token json response");

			throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, null, e);
		}

		return Response.ok(node, MediaType.APPLICATION_JSON_TYPE).cacheControl(cache).header("Pragma", "no-cache").build();
	}

	protected boolean storeToken(Circuit circuit, Message msg, OAuthParameters parsed, OAuth2AccessToken token, String subject, boolean preserveRefresh) throws CircuitAbortException {
		if (Trace.isDebugEnabled()) {
			Trace.debug("Storing the OAuth Access token to persistent store");
		}

		AuthorizationRequest request = new AuthorizationRequest(parsed.toQueryString(new QueryStringHeaderSet()));
		
		if (subject == null) {
			/* XXX if subject is null, token can't be deserialized */
			subject = request.getClientId();
		}
		
		OAuth2Authentication authentication = new OAuth2Authentication(request, subject);
		TokenStore tokenStore = getTokenStore();

		Trace.debug(String.format("storing token authN with: %s ", authentication.getPrincipal()));

		msg.put("accesstoken.authn", authentication);
		msg.put("authentication.subject.id", authentication.getPrincipal());

		token.setAuthenticationSubject(authentication.getUserAuthentication());
		token.setClientID(request.getClientId());
		
		String id_token = token.getAdditionalInformation().get(param_id_token);
		
		if (id_token != null) {
			token.setIdToken(id_token);
		}

		PolicyResource transformer = getAccessTokenTransformer();
		String requested_token_type = (String) parsed.get(param_requested_token_type);

		boolean forceTransform = false;

		if (requested_token_type != null) {
			forceTransform = (!uri_token_type_access_token.equals(requested_token_type)) && (!uri_token_type_refresh_token.equals(requested_token_type));
		}

		if (OAuthServiceEndpoint.isValidPolicy(transformer)) {
			String saved_refresh_token = token.getRefreshToken();

			if (!OAuthServiceEndpoint.invokePolicy(msg, circuit, transformer)) {
				throw new OAuthException(err_invalid_request, null, "the requested token type is not supported");
			}

			/* retrieve issued token type */
			String issued_token_type = token.getAdditionalInformation().remove(param_issued_token_type);

			if (forceTransform && (!requested_token_type.equals(issued_token_type))) {
				throw new OAuthException(err_invalid_request, null, "the requested token type is not supported");
			}
			
			if (preserveRefresh && (saved_refresh_token != null) && (!saved_refresh_token.equals(token.getRefreshToken()))) {
				tokenStore.removeRefreshToken(saved_refresh_token);
				
				preserveRefresh = false;
			}
		} else if (forceTransform) {
			throw new OAuthException(err_invalid_request, null, "the requested token type is not supported");
		}

		if (!forceTransform) {
			/* if we are generating an access token or a refresh token, store them */
			if (!tokenStore.storeAccessToken(token, authentication)) {
				return false;
			}

			if ((!preserveRefresh) && token.hasRefresh()) {
				if (Trace.isDebugEnabled()) {
					Trace.debug("Storing the OAuth Refresh token to persistent store");
				}

				if (!tokenStore.storeRefreshToken(token.getOAuth2RefreshToken(), authentication)) {
					return false;
				}
			}
		}

		if (requested_token_type != null) {
			addAdditionalInfo(token, param_issued_token_type, requested_token_type);
		}

		if (Trace.isDebugEnabled()) {
			Trace.debug("Access token created for: " + subject);
		}

		if (Trace.isVerboseEnabled()) {
			CreateOAuthAccessTokenProcessor.trace(request, authentication, token, 6);
		}

		return true;
	}

	protected void updateAdditionalInfo(Message msg, OAuthParameters parsed, OAuth2AccessToken token) {
		for (OAuthTokenData entry : getAdditionalData()) {
			String key = entry.getKey(msg);

			if (!RESERVED_INFORMATION.contains(key)) {
				addAdditionalInfo(token, key, entry.getValue(msg));
			}
		}

		//addAdditionalInfo(token, param_grant_type, (String) parsed.get(param_grant_type));
		addAdditionalInfo(token, param_code_challenge, (String) parsed.get(param_code_challenge));
		addAdditionalInfo(token, param_code_challenge_method, (String) parsed.get(param_code_challenge_method));

		addAdditionalInfo(token, param_resource, (String) parsed.get(param_resource));
		addAdditionalInfo(token, param_audience, (String) parsed.get(param_audience));
	}

	protected OAuth2AccessToken generateToken(Message msg, Circuit circuit, OAuthParameters parsed, ApplicationDetails details, Set<String> scopes, Map<String, String> data, boolean forceRefresh) throws CircuitAbortException {
		PolicyResource validator = getGrantValidatorCircuit();

		if (OAuthServiceEndpoint.isValidPolicy(validator) && (!OAuthServiceEndpoint.invokePolicy(msg, circuit, validator))) {
			/* trace error, but report invalid credentials */
			Trace.error("Grant Validator returned false");

			throw new OAuthException(err_rfc6749_invalid_grant, null, "provided grant is not valid");
		}

		long accessTokenExpiryInSecs = getAccessTokenExpiration(msg);
		OAuth2AccessToken token = generateToken(msg, accessTokenExpiryInSecs);

		if (data != null) {
			token.setAdditionalInformation(data);
		}
		
		token.setClientID(details.getClientID());
		token.setApplicationID(details.getApplicationID());
		token.setScope(new HashSet<String>(scopes));

		updateAdditionalInfo(msg, parsed, token);
		generateRefreshtoken(msg, circuit, parsed, details, token, forceRefresh);

		return token;
	}

	protected void generateRefreshtoken(Message msg, Circuit circuit, OAuthParameters parsed, ApplicationDetails details, OAuth2AccessToken token, boolean forceRefresh) {
		boolean generateRefresh = forceRefresh || (allowRefreshToken(msg, details) && "NewRefresh".equals(getRefreshTokenChoice(msg)));

		if (!forceRefresh) {
			generateRefresh &= (!refreshRequireOfflineAccess(msg)) || token.getScope().contains("offline_access");
		}

		if (generateRefresh) {
			int refreshTokenLength = getRefreshTokenLength(msg);
			long refreshTokenExpiryInSecs = getRefreshTokenExpiration(msg);

			if (refreshTokenLength < 8) {
				Trace.error("refresh token length is too small");

				throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, null);
			}

			if (refreshTokenExpiryInSecs <= ((long) token.getExpiresIn())) {
				Trace.error("refresh token expiry is less than access token expiry");

				throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, null);
			}

			OAuth2RefreshToken refreshToken = OAuth2RefreshToken.generate(refreshTokenLength, refreshTokenExpiryInSecs, token.getAdditionalInformation());

			refreshToken.setApplicationID(details.getApplicationID());
			token.setOAuth2RefreshToken(refreshToken);
		}
	}

	protected OAuth2AccessToken generateToken(Message msg, OAuth2RefreshToken refresh_token) {
		TokenStore store = getTokenStore();
		String refresh_value = refresh_token.getValue();

		OAuth2Authentication authn = store.readAuthenticationForRefreshToken(refresh_value);

		if (authn == null) {
			Trace.error(String.format("no recorded authentication for refresh token %s", refresh_value));

			throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, null);
		}

		AuthorizationRequest authz = authn.getAuthorizationRequest();

		if (authz == null) {
			Trace.error(String.format("no recorded authorization for refresh token %s", refresh_value));

			throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, null);
		}

		ApplicationDetails details = OAuthGuavaCache.getAppDetailsFromClientId(msg, authz.getClientId());

		if (details == null) {
			Trace.error(String.format("no recorded client for refresh token %s", refresh_value));

			throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, null);
		}

		long accessTokenExpiryInSecs = getAccessTokenExpiration(msg);
		OAuth2AccessToken token = generateToken(msg, accessTokenExpiryInSecs);

		token.setClientID(details.getClientID());
		token.setApplicationID(details.getApplicationID());
		token.setScope(new HashSet<String>(authz.getScope()));
		token.setAdditionalInformation(refresh_token.getAdditionalInformation());
		token.setIdToken(token.getAdditionalInformation().get(param_id_token));
		token.setAuthenticationSubject(authn.getUserAuthentication());

		token.setOAuth2RefreshToken(refresh_token);

		return token;
	}

	private OAuth2AccessToken generateToken(Message msg, long accessTokenExpiryInSecs) {
		String tokenType = getAccessTokenType(msg);
		int accessTokenLength = getAccessTokenLength(msg);

		if (tokenType == null) {
			Trace.error("Unable to substitute access token type");

			throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, null);
		}

		if (accessTokenLength < 8) {
			Trace.error("access token length is too small");

			throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, null);
		}

		if (accessTokenExpiryInSecs <= 0L) {
			Trace.error("access token expiry is zero or negative");

			throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, null);
		}

		OAuth2AccessToken token = OAuth2AccessToken.generate(accessTokenLength, accessTokenExpiryInSecs);

		token.setTokenType(tokenType);

		return token;
	}

	protected OAuth2AccessToken setTokenOnMessage(Message m, OAuth2AccessToken token) {
		if (token != null) {
			m.put("accesstoken", token);
		} else {
			m.remove("accesstoken");

			Trace.error("Empty token passed to setMessageAttributes");
		}

		return token;
	}

	private static void addAdditionalInfo(OAuth2AccessToken token, String key, String value) {
		if (value != null) {
			token.setAdditionalInformation(key, value);
		}
	}
}
