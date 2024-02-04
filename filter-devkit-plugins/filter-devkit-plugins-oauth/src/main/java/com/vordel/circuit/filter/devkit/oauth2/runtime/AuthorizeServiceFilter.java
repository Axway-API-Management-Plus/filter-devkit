package com.vordel.circuit.filter.devkit.oauth2.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.InvocationEngine;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.filter.devkit.context.resources.PolicyResource;
import com.vordel.circuit.filter.devkit.context.resources.SelectorResource;
import com.vordel.circuit.filter.devkit.oauth2.jaxrs.OAuthAccessTokenGenerator;
import com.vordel.circuit.filter.devkit.oauth2.jaxrs.OAuthAuthorizeEndpoint;
import com.vordel.circuit.filter.devkit.oauth2.runtime.TokenServiceFilter.OAuthTokenData;
import com.vordel.circuit.filter.devkit.quick.QuickJavaFilterDefinition;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterComponent;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterField;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterType;
import com.vordel.circuit.filter.devkit.script.jaxrs.ScriptWebComponent;
import com.vordel.circuit.oauth.common.OAuthScopeUtils.ScopesMustMatchSelection;
import com.vordel.circuit.oauth.kps.ApplicationDetails;
import com.vordel.circuit.oauth.store.AuthorizationCodeStore;
import com.vordel.circuit.oauth.store.TokenStore;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;
import com.vordel.mime.Body;
import com.vordel.mime.HeaderSet;

@QuickFilterType(name = "OAuthAuthorizeServiceFilter", resources = "authorizeservice.properties", ui = "authorizeservicepage.xml")
public class AuthorizeServiceFilter extends QuickJavaFilterDefinition {

	private MessageProcessor serviceProcessor;
	private ScriptWebComponent service;

	private TokenStore tokenStore;
	private AuthorizationCodeStore authzCodeCache;

	private Selector<Boolean> enableJsonPOST;
	private Selector<Boolean> enableResponseTypeFilter;
	private Selector<Boolean> forcePKCE;

	private Selector<String> accessTokenType;
	private Selector<Long> accessTokenExpiresInSecs;
	private Selector<Integer> accessTokenLength;

	private Selector<Long> authorizationCodeExpiresInSecs;
	private Selector<Integer> authorizationCodeLength;

	private Selector<String> scopesMustMatch;
	private Selector<String> scopeChoice;
	private PolicyResource scopeCircuitPK;

	@SuppressWarnings("rawtypes")
	private Selector<Set> scopesForToken;
	@SuppressWarnings("rawtypes")
	private Selector<Set> persistentAllowedScopes;
	@SuppressWarnings("rawtypes")
	private Selector<Set> transientAllowedScopes;
	@SuppressWarnings("rawtypes")
	private Selector<Set> discardedScopes;

	private Collection<OAuthTokenData> additionalData;

	private PolicyResource accessTokenTransformer;

	private Selector<Boolean> skipUserConsent;
	private Selector<String> publicIDToken;
	private PolicyResource requestRetriever;
	private PolicyResource requestValidator;
	private PolicyResource redirectGenerator;
	private PolicyResource authenticationPolicy;
	private PolicyResource authorizationPolicy;
	private PolicyResource publicResourceOwnerGenerator;

	@QuickFilterField(name = "serviceName", cardinality = "1", type = "string", defaults = "OAuth 2.0 Authorization Service")
	private void setServiceName(ConfigContext ctx, Entity entity, String field) {
		/* entity configuration for inner service context processor */
	}

	@QuickFilterField(name = "metricsMask", cardinality = "1", type = "integer", defaults = "3")
	private void setMetricsMask(ConfigContext ctx, Entity entity, String field) {
		/* entity configuration for inner service context processor */
	}

	@QuickFilterField(name = "compositeContext", cardinality = "1", type = "boolean", defaults = "0")
	private void setCompositecontext(ConfigContext ctx, Entity entity, String field) {
		/* entity configuration for inner service context processor */
	}

	@QuickFilterField(name = "clientAttributeName", cardinality = "1", type = "string", defaults = "authentication.subject.id")
	private void setClientAttributeName(ConfigContext ctx, Entity entity, String field) {
		/* entity configuration for inner service context processor */
	}

	@QuickFilterField(name = "authzCodeCache", cardinality = "1", type = "^AuthzCodePersist")
	private void setAuthorizationCodeStore(ConfigContext ctx, Entity entity, String field) {
		authzCodeCache = TokenServiceFilter.getAuthorizationCodeStore(entity.getReferenceValue(field));
	}

	@QuickFilterField(name = "tokenStore", cardinality = "1", type = "^AccessTokenPersist")
	private void setTokenStore(ConfigContext ctx, Entity entity, String field) {
		tokenStore = TokenServiceFilter.getTokenStore(entity.getReferenceValue(field));
	}

	@QuickFilterField(name = "enableJsonPOST", cardinality = "?", type = "boolean", defaults = "0")
	private void setEnableJsonPOST(ConfigContext ctx, Entity entity, String field) {
		enableJsonPOST = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "enableResponseTypeFilter", cardinality = "?", type = "boolean", defaults = "0")
	private void setEnableResponseTypeFilter(ConfigContext ctx, Entity entity, String field) {
		enableResponseTypeFilter = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "forcePKCE", cardinality = "?", type = "boolean", defaults = "0")
	private void setForcePKCE(ConfigContext ctx, Entity entity, String field) {
		forcePKCE = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "scopesMustMatch", cardinality = "1", type = "string", defaults = "Any")
	private void setScopesMustMatch(ConfigContext ctx, Entity entity, String field) {
		scopesMustMatch = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, false);
	}

	@QuickFilterField(name = "scopeChoice", cardinality = "1", type = "string", defaults = "Application")
	private void setScopeChoice(ConfigContext ctx, Entity entity, String field) {
		scopeChoice = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, false);
	}

	@QuickFilterField(name = "scopeCircuitPK", cardinality = "?", type = "^FilterCircuit")
	private void setScopeCircuitPK(ConfigContext ctx, Entity entity, String field) {
		scopeCircuitPK = new PolicyResource(ctx, entity, field);
	}

	@QuickFilterField(name = "scopesForToken", cardinality = "?", type = "string", defaults = "${scopes.for.token}")
	private void setScopesForToken(ConfigContext ctx, Entity entity, String field) {
		scopesForToken = SelectorResource.fromLiteral(entity.getStringValue(field), Set.class, false);
	}

	@QuickFilterField(name = "persistentAllowedScopes", cardinality = "?", type = "string", defaults = "${scopes.allowed.persistent}")
	private void setPersistentAllowedScopes(ConfigContext ctx, Entity entity, String field) {
		persistentAllowedScopes = SelectorResource.fromLiteral(entity.getStringValue(field), Set.class, false);
	}

	@QuickFilterField(name = "transientAllowedScopes", cardinality = "?", type = "string", defaults = "${scopes.allowed.transient}")
	private void setTransientAllowedScopes(ConfigContext ctx, Entity entity, String field) {
		transientAllowedScopes = SelectorResource.fromLiteral(entity.getStringValue(field), Set.class, false);
	}

	@QuickFilterField(name = "discardedScopes", cardinality = "?", type = "string", defaults = "${scopes.discarded}")
	private void setDiscardedScopes(ConfigContext ctx, Entity entity, String field) {
		discardedScopes = SelectorResource.fromLiteral(entity.getStringValue(field), Set.class, false);
	}

	@QuickFilterComponent(name = "Property")
	private void setAdditionalData(ConfigContext ctx, Entity entity, Collection<ESPK> additionalData) {
		this.additionalData = TokenServiceFilter.properties(ctx.getStore(), additionalData);
	}

	@QuickFilterField(name = "accessTokenType", cardinality = "1", type = "string", defaults = "Bearer")
	private void setAccessTokenType(ConfigContext ctx, Entity entity, String field) {
		accessTokenType = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, false);
	}

	@QuickFilterField(name = "accessTokenExpiresInSecs", cardinality = "1", type = "long", defaults = "3600")
	private void setAccessTokenExpiresInSecs(ConfigContext ctx, Entity entity, String field) {
		accessTokenExpiresInSecs = SelectorResource.fromLiteral(entity.getStringValue(field), Long.class, false);
	}

	@QuickFilterField(name = "accessTokenlength", cardinality = "1", type = "integer", defaults = "54")
	private void setAccessTokenlength(ConfigContext ctx, Entity entity, String field) {
		accessTokenLength = SelectorResource.fromLiteral(entity.getStringValue(field), Integer.class, false);
	}

	@QuickFilterField(name = "authorizationCodeExpiresInSecs", cardinality = "1", type = "long", defaults = "3600")
	private void setAuthorizationCodeExpiresInSecs(ConfigContext ctx, Entity entity, String field) {
		authorizationCodeExpiresInSecs = SelectorResource.fromLiteral(entity.getStringValue(field), Long.class, false);
	}

	@QuickFilterField(name = "authorizationCodelength", cardinality = "1", type = "integer", defaults = "54")
	private void setAuthorizationCodelength(ConfigContext ctx, Entity entity, String field) {
		authorizationCodeLength = SelectorResource.fromLiteral(entity.getStringValue(field), Integer.class, false);
	}

	@QuickFilterField(name = "accessTokenTransformer", cardinality = "?", type = "^FilterCircuit")
	private void setAccessTokenTransformer(ConfigContext ctx, Entity entity, String field) {
		accessTokenTransformer = new PolicyResource(ctx, entity, field);
	}

	@QuickFilterField(name = "skipUserConsent", cardinality = "?", type = "boolean", defaults = "0")
	private void setSkipUserConsent(ConfigContext ctx, Entity entity, String field) {
		skipUserConsent = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "publicIDToken", cardinality = "1", type = "string", defaults = "${oauth.id_token.public}")
	private void setPublicIDToken(ConfigContext ctx, Entity entity, String field) {
		publicIDToken = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, false);
	}

	@QuickFilterField(name = "requestRetriever", cardinality = "?", type = "^FilterCircuit")
	private void setRequestRetriever(ConfigContext ctx, Entity entity, String field) {
		requestRetriever = new PolicyResource(ctx, entity, field);
	}

	@QuickFilterField(name = "requestValidator", cardinality = "?", type = "^FilterCircuit")
	private void setRequestValidator(ConfigContext ctx, Entity entity, String field) {
		requestValidator = new PolicyResource(ctx, entity, field);
	}

	@QuickFilterField(name = "redirectGenerator", cardinality = "?", type = "^FilterCircuit")
	private void setRedirectGenerator(ConfigContext ctx, Entity entity, String field) {
		redirectGenerator = new PolicyResource(ctx, entity, field);
	}

	@QuickFilterField(name = "authenticationPolicy", cardinality = "?", type = "^FilterCircuit")
	private void setAuthenticationPolicy(ConfigContext ctx, Entity entity, String field) {
		authenticationPolicy = new PolicyResource(ctx, entity, field);
	}

	@QuickFilterField(name = "authorizationPolicy", cardinality = "?", type = "^FilterCircuit")
	private void setAuthorizationPolicy(ConfigContext ctx, Entity entity, String field) {
		authorizationPolicy = new PolicyResource(ctx, entity, field);
	}

	@QuickFilterField(name = "publicResourceOwnerGenerator", cardinality = "?", type = "^FilterCircuit")
	private void setPublicResourceOwnerGenerator(ConfigContext ctx, Entity entity, String field) {
		publicResourceOwnerGenerator = new PolicyResource(ctx, entity, field);
	}

	private OAuthAccessTokenGenerator createOAuthAccessTokenGenerator() {
		return new OAuthAccessTokenGenerator() {
			@Override
			protected Set<?> substituteCircuitScopes(Message msg) {
				Set<?> scopes = scopesForToken == null ? null : scopesForToken.substitute(msg);

				return scopes == null ? Collections.emptySet() : scopes;
			}

			@Override
			protected ScopesMustMatchSelection getScopesMustMatchSelection(Message msg) {
				String selection = scopesMustMatch == null ? null : scopesMustMatch.substitute(msg);

				return selection == null ? ScopesMustMatchSelection.Any : ScopesMustMatchSelection.valueOf(selection);
			}

			@Override
			protected PolicyResource getScopeValidator() {
				return scopeCircuitPK;
			}

			@Override
			protected String getScopesFrom(Message msg) {
				String from = scopeChoice == null ? null : scopeChoice.substitute(msg);

				return from == null ? "Application" : from;
			}

			@Override
			protected TokenStore getTokenStore() {
				return tokenStore;
			}

			@Override
			protected Collection<OAuthTokenData> getAdditionalData() {
				return additionalData;
			}

			@Override
			protected long getAccessTokenExpiration(Message msg) {
				Long expiration = accessTokenExpiresInSecs.substitute(msg);

				return expiration == null ? 0 : expiration;
			}

			@Override
			protected int getAccessTokenLength(Message msg) {
				Integer length = accessTokenLength.substitute(msg);

				return length == null ? 0 : length;
			}

			@Override
			protected String getAccessTokenType(Message msg) {
				return accessTokenType.substitute(msg);
			}

			@Override
			protected String getRefreshTokenChoice(Message msg) {
				return "NoRefresh";
			}

			@Override
			protected long getRefreshTokenExpiration(Message msg) {
				return -1;
			}

			@Override
			protected int getRefreshTokenLength(Message msg) {
				return -1;
			}

			@Override
			protected boolean refreshRequireOfflineAccess(Message msg) {
				return false;
			}

			@Override
			protected boolean allowRefreshToken(Message msg, ApplicationDetails details) {
				return false;
			}

			@Override
			protected PolicyResource getGrantValidatorCircuit() {
				return null;
			}

			@Override
			protected PolicyResource getAccessTokenTransformer() {
				return accessTokenTransformer;
			}

			@Override
			protected String getPreserveChoice(Message msg) {
				return null;
			}

			@Override
			protected AuthorizationCodeStore getAuthorizationCodeStore() {
				return authzCodeCache;
			}

			@Override
			protected Set<?> getTransientAllowedScopes(Message msg) {
				Set<?> scopes = transientAllowedScopes == null ? null : transientAllowedScopes.substitute(msg);

				return scopes == null ? Collections.emptySet() : scopes;
			}

			@Override
			protected Set<?> getDiscardedScopes(Message msg) {
				Set<?> scopes = discardedScopes == null ? null : discardedScopes.substitute(msg);

				return scopes == null ? Collections.emptySet() : scopes;
			}

			@Override
			protected PolicyResource getAuthorizationPolicy() {
				return authorizationPolicy;
			}

		};
	}

	private OAuthAuthorizeEndpoint attachOAuthTokenEndpoint(OAuthAccessTokenGenerator generator) {
		return new OAuthAuthorizeEndpoint() {
			@Override
			protected boolean enableJsonPOST(Message msg) {
				return TokenServiceFilter.substituteBoolean(msg, enableJsonPOST, false);
			}

			@Override
			protected boolean enableResponseTypeFilter(Message msg) {
				return TokenServiceFilter.substituteBoolean(msg, enableResponseTypeFilter, false);
			}

			@Override
			protected boolean forcePKCE(Message msg) {
				return TokenServiceFilter.substituteBoolean(msg, forcePKCE, false);
			}

			@Override
			protected boolean skipUserConsent(Message msg) {
				return TokenServiceFilter.substituteBoolean(msg, skipUserConsent, false);
			}

			@Override
			protected PolicyResource getRequestRetriever() {
				return requestRetriever;
			}

			@Override
			protected PolicyResource getRequestValidator() {
				return requestValidator;
			}

			@Override
			protected PolicyResource getRedirectGenerator() {
				return redirectGenerator;
			}

			@Override
			protected PolicyResource getAuthenticationPolicy() {
				return authenticationPolicy;
			}

			@Override
			protected PolicyResource getAuthorizationPolicy() {
				return authorizationPolicy;
			}

			@Override
			protected PolicyResource getPublicResourceOwnerGenerator() {
				return publicResourceOwnerGenerator;
			}

			@Override
			protected String getPublicResourceOwner(Message msg) {
				return publicIDToken == null ? null : publicIDToken.substitute(msg);
			}

			@Override
			protected Set<?> getTransientAllowedScopes(Message msg) {
				Set<?> scopes = transientAllowedScopes == null ? null : transientAllowedScopes.substitute(msg);

				return scopes == null ? Collections.emptySet() : scopes;
			}

			@Override
			protected Set<?> getPersistentAllowedScopes(Message msg) {
				Set<?> scopes = persistentAllowedScopes == null ? null : persistentAllowedScopes.substitute(msg);

				return scopes == null ? Collections.emptySet() : scopes;
			}

			@Override
			protected Set<?> getDiscardedScopes(Message msg) {
				Set<?> scopes = discardedScopes == null ? null : discardedScopes.substitute(msg);

				return scopes == null ? Collections.emptySet() : scopes;
			}

			@Override
			protected int getAuthorizationCodeLength(Message msg) {
				Integer length = authorizationCodeLength.substitute(msg);

				return length == null ? 0 : length;
			}

			@Override
			protected long getAuthorizationCodeExpiration(Message msg) {
				Long expiration = authorizationCodeExpiresInSecs.substitute(msg);

				return expiration == null ? 0 : expiration;
			}

			@Override
			protected OAuthAccessTokenGenerator getOAuthAccessTokenGenerator() {
				return generator;
			}
		};
	}

	@Override
	public void attachFilter(ConfigContext ctx, Entity entity) {
		if (tokenStore == null) {
			throw new EntityStoreException("missing OAuth Token Store");
		}

		this.serviceProcessor = TokenServiceFilter.attachServiceContextProcessor(ctx, entity);
		this.service = ScriptWebComponent.createWebComponent(attachOAuthTokenEndpoint(createOAuthAccessTokenGenerator()));
	}

	@Override
	public boolean invokeFilter(Circuit c, Message m, MessageProcessor p) throws CircuitAbortException {
		try {
			/* set message service context */
			InvocationEngine.invokeFilter(c, serviceProcessor, m, p, null);

			return service.service(m);
		} finally {
			/* move Cache-Control and Pragma to body after service execution */
			Body body = (Body) m.get(MessageProperties.CONTENT_BODY);

			if (body != null) {
				HeaderSet headers = (HeaderSet) m.get(MessageProperties.HTTP_HEADERS);

				if (headers != null) {
					TokenServiceFilter.relay(headers, body.getHeaders(), "Cache-Control");
					TokenServiceFilter.relay(headers, body.getHeaders(), "Pragma");
				}
			}
		}
	}

	@Override
	public void detachFilter() {
		serviceProcessor.filterDetached();
		serviceProcessor = null;
		service = null;
	}

}
