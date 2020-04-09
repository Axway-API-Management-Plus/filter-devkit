package com.vordel.sdk.plugins.oauth2.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.DefaultFilter;
import com.vordel.circuit.FilterContainerImpl;
import com.vordel.circuit.InvocationEngine;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.declarative.FilterFactory;
import com.vordel.circuit.ext.filter.quick.QuickFilterComponent;
import com.vordel.circuit.ext.filter.quick.QuickFilterField;
import com.vordel.circuit.ext.filter.quick.QuickFilterType;
import com.vordel.circuit.ext.filter.quick.QuickJavaFilterDefinition;
import com.vordel.circuit.oauth.common.OAuthScopeUtils.ScopesMustMatchSelection;
import com.vordel.circuit.oauth.kps.ApplicationDetails;
import com.vordel.circuit.oauth.persistence.OauthLoadableCache;
import com.vordel.circuit.oauth.store.AuthorizationCodeStore;
import com.vordel.circuit.oauth.store.TokenStore;
import com.vordel.circuit.script.context.MessageContextTracker;
import com.vordel.circuit.script.context.resources.PolicyResource;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.circuit.script.jaxrs.ScriptWebComponent;
import com.vordel.common.Dictionary;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.dwe.DelayedESPK;
import com.vordel.el.Selector;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;
import com.vordel.mime.Body;
import com.vordel.mime.HeaderSet;
import com.vordel.mime.Headers;
import com.vordel.sdk.plugins.oauth2.jaxrs.OAuthAccessTokenGenerator;
import com.vordel.sdk.plugins.oauth2.jaxrs.OAuthServiceEndpoint;
import com.vordel.sdk.plugins.oauth2.jaxrs.OAuthTokenEndpoint;

@QuickFilterType(name = "OAuthTokenServiceFilter", resources = "tokenservice.properties", ui = "tokenservicepage.xml")
public class TokenServiceFilter extends QuickJavaFilterDefinition {
	private final List<Selector<String>> extendedGrantNames = new ArrayList<Selector<String>>();

	private MessageProcessor serviceProcessor;
	private ScriptWebComponent service;

	private Selector<Boolean> enableFormClientAuth;
	private Selector<Boolean> enableBasicClientAuth;
	private Selector<Boolean> enableClientSecretQueryCheck;
	private Selector<Boolean> forceClientSecret;
	private Selector<Boolean> enableClientAuthFilter;
	private Selector<Boolean> enableHttpGET;
	private Selector<Boolean> enableJsonPOST;

	private PolicyResource clientAssertionValidator;
	private PolicyResource clientAuthorizationValidator;
	private Selector<Boolean> enableGrantTypeFilter;

	private TokenStore tokenStore;
	private AuthorizationCodeStore authzCodeCache;

	private Selector<String> accessTokenType;
	private Selector<Long> accessTokenExpiresInSecs;
	private Selector<Integer> accessTokenLength;
	private Selector<String> refreshChoice;
	private Selector<Long> refreshTokenExpiresInSecs;
	private Selector<Integer> refreshTokenlength;
	private Selector<String> preserveChoice;
	private Selector<String> scopesMustMatch;
	private Selector<String> scopeChoice;
	private PolicyResource scopeCircuitPK;
	private Selector<Boolean> allowOpenIDScope;
	private Selector<Boolean> skipUserConsent;

	private Selector<Boolean> refreshRequireOfflineAccess;

	private Selector<Boolean> allowPublicClientCredentials;

	@SuppressWarnings("rawtypes")
	private Selector<Set> scopesForToken;

	private Collection<OAuthTokenData> additionalData;

	private PolicyResource grantAuthenticatorCircuit;

	private PolicyResource grantValidatorCircuit;

	private PolicyResource grantDecoderCircuit;

	private PolicyResource accessTokenTransformer;

	@QuickFilterField(name = "serviceName", cardinality = "1", type = "string", defaults = "OAuth 2.0 Token Service")
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

	@QuickFilterField(name = "enableClientAuthFilter", cardinality = "?", type = "integer", defaults = "0")
	private void setEnableClientAuthFilter(ConfigContext ctx, Entity entity, String field) {
		enableClientAuthFilter = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "enableFormClientAuth", cardinality = "?", type = "boolean", defaults = "1")
	private void setEnableFormClientAuth(ConfigContext ctx, Entity entity, String field) {
		enableFormClientAuth = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "enableClientSecretQueryCheck", cardinality = "?", type = "boolean", defaults = "1")
	private void setEnableClientSecretQueryCheck(ConfigContext ctx, Entity entity, String field) {
		enableClientSecretQueryCheck = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "forceClientSecret", cardinality = "?", type = "boolean", defaults = "0")
	private void setForceClientSecret(ConfigContext ctx, Entity entity, String field) {
		forceClientSecret = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "enableBasicClientAuth", cardinality = "?", type = "boolean", defaults = "1")
	private void setEnableBasicClientAuth(ConfigContext ctx, Entity entity, String field) {
		enableBasicClientAuth = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "clientAssertionValidator", cardinality = "?", type = "^FilterCircuit")
	private void setClientAssertionValidator(ConfigContext ctx, Entity entity, String field) {
		clientAssertionValidator = new PolicyResource(ctx, entity, field);
	}

	@QuickFilterField(name = "clientAuthorizationValidator", cardinality = "?", type = "^FilterCircuit")
	private void setClientAuthorizationValidator(ConfigContext ctx, Entity entity, String field) {
		clientAuthorizationValidator = new PolicyResource(ctx, entity, field);
	}

	@QuickFilterField(name = "enableHttpGET", cardinality = "?", type = "boolean", defaults = "0")
	private void setEnableHttpGET(ConfigContext ctx, Entity entity, String field) {
		enableHttpGET = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "enableJsonPOST", cardinality = "?", type = "boolean", defaults = "0")
	private void setEnableJsonPOST(ConfigContext ctx, Entity entity, String field) {
		enableJsonPOST = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "enableGrantTypeFilter", cardinality = "?", type = "boolean", defaults = "0")
	private void setEnableGrantTypeFilter(ConfigContext ctx, Entity entity, String field) {
		enableGrantTypeFilter = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "authzCodeCache", cardinality = "1", type = "^AuthzCodePersist")
	private void setAuthorizationCodeStore(ConfigContext ctx, Entity entity, String field) {
		authzCodeCache = getAuthorizationCodeStore(entity.getReferenceValue(field));
	}

	@QuickFilterField(name = "tokenStore", cardinality = "1", type = "^AccessTokenPersist")
	private void setTokenStore(ConfigContext ctx, Entity entity, String field) {
		tokenStore = getTokenStore(entity.getReferenceValue(field));
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

	@QuickFilterField(name = "refreshChoice", cardinality = "1", type = "string", defaults = "NewRefresh")
	private void setRefreshChoice(ConfigContext ctx, Entity entity, String field) {
		refreshChoice = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, false);
	}

	@QuickFilterField(name = "preserveChoice", cardinality = "1", type = "string", defaults = "Preserve")
	private void setPreserveChoice(ConfigContext ctx, Entity entity, String field) {
		preserveChoice = SelectorResource.fromLiteral(entity.getStringValue(field), String.class, false);
	}

	@QuickFilterField(name = "refreshTokenExpiresInSecs", cardinality = "1", type = "long", defaults = "43200")
	private void setRefreshTokenExpiresInSecs(ConfigContext ctx, Entity entity, String field) {
		refreshTokenExpiresInSecs = SelectorResource.fromLiteral(entity.getStringValue(field), Long.class, false);
	}

	@QuickFilterField(name = "refreshTokenlength", cardinality = "1", type = "integer", defaults = "46")
	private void setRefreshTokenlength(ConfigContext ctx, Entity entity, String field) {
		refreshTokenlength = SelectorResource.fromLiteral(entity.getStringValue(field), Integer.class, false);
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

	@QuickFilterField(name = "allowOpenIDScope", cardinality = "?", type = "boolean", defaults = "0")
	private void setAllowOpenIDScope(ConfigContext ctx, Entity entity, String field) {
		allowOpenIDScope = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "skipUserConsent", cardinality = "?", type = "boolean", defaults = "0")
	private void setSkipUserConsent(ConfigContext ctx, Entity entity, String field) {
		skipUserConsent = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterComponent(name="Property")
	private void setAdditionalData(ConfigContext ctx, Entity entity, Collection<ESPK> additionalData) {
		this.additionalData = properties(ctx.getStore(), additionalData);
	}

	@QuickFilterField(name = "extendedGrantNames", cardinality = "*", type = "string")
	private void setExtendedGrants(ConfigContext ctx, Entity entity, String field) {
		Collection<String> values = entity.getStringValues(field);

		extendedGrantNames.clear();

		if (values != null) {
			for(String value : values) {
				extendedGrantNames.add(SelectorResource.fromLiteral(value, String.class, false));
			}
		}
	}

	@QuickFilterField(name = "refreshRequireOfflineAccess", cardinality = "?", type = "boolean", defaults = "0")
	private void setRefreshRequireOfflineAccess(ConfigContext ctx, Entity entity, String field) {
		refreshRequireOfflineAccess = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "allowPublicClientCredentials", cardinality = "?", type = "boolean", defaults = "0")
	private void allowPublicClientCredentials(ConfigContext ctx, Entity entity, String field) {
		allowPublicClientCredentials = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "grantDecoderCircuit", cardinality = "?", type = "^FilterCircuit")
	private void setExtendedGrantCircuit(ConfigContext ctx, Entity entity, String field) {
		grantDecoderCircuit = new PolicyResource(ctx, entity, field);
	}

	@QuickFilterField(name = "grantAuthenticator", cardinality = "?", type = "^FilterCircuit")
	private void setGrantAuthenticator(ConfigContext ctx, Entity entity, String field) {
		grantAuthenticatorCircuit = new PolicyResource(ctx, entity, field);
	}

	@QuickFilterField(name = "grantValidator", cardinality = "?", type = "^FilterCircuit")
	private void setGrantValidator(ConfigContext ctx, Entity entity, String field) {
		grantValidatorCircuit = new PolicyResource(ctx, entity, field);
	}

	@QuickFilterField(name = "accessTokenTransformer", cardinality = "?", type = "^FilterCircuit")
	private void setAccessTokenTransformer(ConfigContext ctx, Entity entity, String field) {
		accessTokenTransformer = new PolicyResource(ctx, entity, field);
	}

	public static TokenStore getTokenStore(ESPK delegatedPK) {
		TokenStore store = null;

		if (delegatedPK != null) {
			DelayedESPK delayedPK = delegatedPK instanceof DelayedESPK ? (DelayedESPK) delegatedPK: new DelayedESPK(delegatedPK);
			ESPK storePK = delayedPK.substitute(Dictionary.empty);

			if (!EntityStore.ES_NULL_PK.equals(storePK)) {
				OauthLoadableCache storeCache = OauthLoadableCache.getInstance();

				if (storeCache == null) {
					throw new EntityStoreException("OAuth stores have not been loaded yet");
				}

				store = storeCache.getAccessTokenStore(storePK);

				if (store == null) {
					throw new EntityStoreException("Failed to find an OAuth Access token store with given entity pk", storePK);
				}
			}
		}

		return store;
	}

	public static AuthorizationCodeStore getAuthorizationCodeStore(ESPK delegatedPK) {
		AuthorizationCodeStore store = null;

		if (delegatedPK != null) {
			DelayedESPK delayedPK = delegatedPK instanceof DelayedESPK ? (DelayedESPK) delegatedPK: new DelayedESPK(delegatedPK);
			ESPK storePK = delayedPK.substitute(Dictionary.empty);

			if (!EntityStore.ES_NULL_PK.equals(storePK)) {
				OauthLoadableCache storeCache = OauthLoadableCache.getInstance();

				if (storeCache == null) {
					throw new EntityStoreException("OAuth stores have not been loaded yet");
				}

				store = storeCache.getAuthzCodeStore(storePK);

				if (store == null) {
					throw new EntityStoreException("Failed to find an OAuth Authorization Code store with given entity pk", storePK);
				}
			}
		}

		return store;
	}


	/**
	 * instanciate and attach a service context processor
	 * 
	 * @param ctx
	 * @param entity
	 * @return
	 */
	private MessageProcessor attachServiceContextProcessor(ConfigContext ctx, Entity entity) {
		try {
			EntityStore es = ctx.getStore();
			DefaultFilter filter = (DefaultFilter) FilterFactory.createFilter(es, es.getTypeForName("ServiceContextFilter"), entity);

			filter.configure(ctx, entity);
			filter.setName("Set OAuth Service Context");

			MessageProcessor processor = (MessageProcessor) FilterContainerImpl.createFilterContainer(filter.getMessageProcessorClass(), filter, entity.getParentPK());

			processor.filterAttached(ctx, entity);

			return processor;
		} catch (InstantiationException e) {
			throw new EntityStoreException("unable create service processor", e);
		} catch (ClassNotFoundException e) {
			throw new EntityStoreException("unable create service processor", e);
		} catch (IllegalAccessException e) {
			throw new EntityStoreException("unable create service processor", e);
		}
	}

	private Boolean substituteBoolean(Message msg, Selector<Boolean> selector, Boolean defaultValue) {
		Boolean value = selector == null ? defaultValue : selector.substitute(msg);

		return value == null ? defaultValue : value;
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
				String choice = refreshChoice.substitute(msg);

				return choice == null ? "NoRefresh" : choice;
			}

			@Override
			protected long getRefreshTokenExpiration(Message msg) {
				Long expiration = refreshTokenExpiresInSecs.substitute(msg);

				return expiration == null ? 0 : expiration;
			}

			@Override
			protected int getRefreshTokenLength(Message msg) {
				Integer length = refreshTokenlength.substitute(msg);

				return length == null ? 0 : length;
			}

			@Override
			protected boolean refreshRequireOfflineAccess(Message msg) {
				return substituteBoolean(msg, refreshRequireOfflineAccess, false);
			}

			@Override
			protected boolean allowRefreshToken(Message msg, ApplicationDetails details) {
				return (!enableGrantTypeFilter(msg)) || (OAuthServiceEndpoint.isGrantTypeAllowed(details, "refresh_token"));
			}

			@Override
			protected PolicyResource getGrantValidatorCircuit() {
				return grantValidatorCircuit;
			}

			@Override
			protected PolicyResource getAccessTokenTransformer() {
				return accessTokenTransformer;
			}

			@Override
			protected String getPreserveChoice(Message msg) {
				String choice = preserveChoice.substitute(msg);

				return choice;
			}

			@Override
			protected AuthorizationCodeStore getAuthorizationCodeStore() {
				return authzCodeCache;
			}
		};
	}

	private OAuthTokenEndpoint attachOAuthTokenEndpoint(OAuthAccessTokenGenerator generator) {
		return new OAuthTokenEndpoint() {
			@Override
			protected boolean allowGET(Message msg) {
				return substituteBoolean(msg, enableHttpGET, false);
			}

			@Override
			protected boolean enableJsonPOST(Message msg) {
				return substituteBoolean(msg, enableJsonPOST, false);
			}

			@Override
			public PolicyResource getClientAssertionValidator() {
				return clientAssertionValidator;
			}

			@Override
			public PolicyResource getClientAuthorizationValidator() {
				return clientAuthorizationValidator;
			}

			@Override
			protected boolean enableClientSecretQueryCheck(Message msg) {
				return substituteBoolean(msg, enableClientSecretQueryCheck, true);
			}

			@Override
			protected boolean enableFormClientAuth(Message msg) {
				return enableClientAuthFilter(msg) ? true : substituteBoolean(msg, enableFormClientAuth, true);
			}

			@Override
			protected boolean enableBasicClientAuth(Message msg) {
				return enableClientAuthFilter(msg) ? true : substituteBoolean(msg, enableBasicClientAuth, true);
			}

			@Override
			protected boolean forceClientSecret(Message msg) {
				return substituteBoolean(msg, forceClientSecret, false);
			}

			@Override
			protected boolean enableClientAuthFilter(Message msg) {
				return substituteBoolean(msg, enableClientAuthFilter, false);
			}

			@Override
			protected boolean enableGrantTypeFilter(Message msg) {
				return TokenServiceFilter.this.enableGrantTypeFilter(msg);
			}

			@Override
			protected boolean skipUserConsent(Message msg) {
				return substituteBoolean(msg, skipUserConsent, false);
			}

			@Override
			protected boolean allowOpenIDScope(Message msg) {
				return substituteBoolean(msg, allowOpenIDScope, false);
			}

			@Override
			protected boolean isExtendedGrantType(Message msg, String grant_type) {
				boolean found = false;

				if (grant_type != null) {
					Iterator<Selector<String>> iterator = extendedGrantNames.iterator();

					while((!found) && iterator.hasNext()) {
						Selector<String> selector = iterator.next();

						found |= grant_type.equals(selector.substitute(msg));
					}
				}

				return false;
			}

			@Override
			protected boolean allowPublicClientCredentials(Message msg) {
				return substituteBoolean(msg, allowPublicClientCredentials, false);
			}

			@Override
			protected OAuthAccessTokenGenerator getOAuthAccessTokenGenerator() {
				return generator;
			}

			@Override
			protected PolicyResource getGrantAuthenticatorCircuit() {
				return grantAuthenticatorCircuit;
			}

			@Override
			protected PolicyResource getGrantDecoderCircuit() {
				return grantDecoderCircuit;
			}
		};
	}

	private boolean enableGrantTypeFilter(Message msg) {
		return substituteBoolean(msg, enableGrantTypeFilter, false);
	}

	@Override
	public void attachFilter(ConfigContext ctx, Entity entity) {
		if (tokenStore == null) {
			throw new EntityStoreException("missing OAuth Token Store");
		}
		this.serviceProcessor = attachServiceContextProcessor(ctx, entity);
		this.service = ScriptWebComponent.createWebComponent(attachOAuthTokenEndpoint(createOAuthAccessTokenGenerator()));
	}

	@Override
	public void detachFilter() {
		serviceProcessor.filterDetached();
		serviceProcessor = null;
		service = null;

		extendedGrantNames.clear();
	}

	@Override
	public boolean invokeFilter(Circuit c, Message m) throws CircuitAbortException {
		try {
			/* set message service context */
			InvocationEngine.invokeFilter(c, serviceProcessor, m, MessageContextTracker.getMessageContextTracker(m).getMessageProcessor(), null);

			return service.service(m);
		} finally {
			/* move Cache-Control and Pragma to body after service execution */
			Body body = (Body) m.get(MessageProperties.CONTENT_BODY);

			if (body != null) {
				HeaderSet headers = (HeaderSet) m.get(MessageProperties.HTTP_HEADERS);

				if (headers != null) {
					relay(headers, body.getHeaders(), "Cache-Control");
					relay(headers, body.getHeaders(), "Pragma");
				}
			}
		}
	}

	private static void relay(HeaderSet from, Headers to, String name) {
		Iterator<String> iterator = from.getHeaders(name);

		if (iterator != null){
			while(iterator.hasNext()) {
				String value = iterator.next();

				if (!value.isEmpty()) {
					to.addHeader(name, value);
				}

				iterator.remove();
			}

			from.remove(name);
		}
	}

	private static Collection<OAuthTokenData> properties(EntityStore es, Collection<ESPK> properties) {
		List<OAuthTokenData> result = new ArrayList<OAuthTokenData>();

		for (ESPK element : properties) {
			Entity entity = es.getEntity(element);

			String key = entity.getStringValue("name");
			String value = entity.getStringValue("value");

			if ((key != null) && (value != null)) {
				Selector<String> keyExpression = SelectorResource.fromLiteral(key, String.class, true);
				Selector<String> valueExpression = SelectorResource.fromLiteral(value, String.class, false);

				if ((keyExpression != null) && (valueExpression != null)) {
					OAuthTokenData property = new OAuthTokenData(keyExpression, valueExpression);

					result.add(property);
				}
			}
		}

		if (result.isEmpty()) {
			result = Collections.emptyList();
		}

		return result;
	}

	public static class OAuthTokenData {
		private final Selector<String> key;
		private final Selector<String> value;

		private OAuthTokenData(Selector<String> key, Selector<String> value) {
			this.key = key;
			this.value = value;
		}

		public String getKey(Message message) {
			return key.substitute(message);
		}

		public String getValue(Message message) {
			return value.substitute(message);
		}
	}
}
