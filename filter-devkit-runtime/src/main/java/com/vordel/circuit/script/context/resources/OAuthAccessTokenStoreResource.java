package com.vordel.circuit.script.context.resources;

import java.util.Collection;

import com.vordel.circuit.oauth.persistence.OauthLoadableCache;
import com.vordel.circuit.oauth.store.TokenStore;
import com.vordel.circuit.oauth.token.OAuth2AccessToken;
import com.vordel.circuit.oauth.token.OAuth2Authentication;
import com.vordel.circuit.oauth.token.OAuth2RefreshToken;
import com.vordel.config.ConfigContext;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;

public class OAuthAccessTokenStoreResource implements ContextResource, TokenStore {
	private final TokenStore accessTokenStore;

	public OAuthAccessTokenStoreResource(ConfigContext ctx, Entity entity, String reference) throws EntityStoreException {
		this(ctx, entity.getReferenceValue(reference));
	}

	public OAuthAccessTokenStoreResource(ConfigContext ctx, ESPK reference) {
		this(getAuthzCodeStore(reference));
	}

	public OAuthAccessTokenStoreResource(TokenStore accessTokenStore) {
		this.accessTokenStore = accessTokenStore;
	}

	protected static TokenStore getAuthzCodeStore(ESPK key) throws EntityStoreException {
		if ((key == null) || (EntityStore.ES_NULL_PK.equals(key))) {
			throw new EntityStoreException("Can not look up access token store with NULL PK");
		}
		OauthLoadableCache storesCache = OauthLoadableCache.getInstance();

		if (storesCache == null) {
			throw new EntityStoreException("OAuth stores have not been loaded yet");
		}

		TokenStore accessTokenStore = storesCache.getAccessTokenStore(key);

		if (accessTokenStore == null) {
			throw new EntityStoreException("Failed to find an OAuth Authorization Code store with given entity pk", new ESPK[] {
					key });
		}

		return accessTokenStore;
	}

	@Override
	public Collection<OAuth2AccessToken> findTokensByClientId(String clientId) {
		return accessTokenStore.findTokensByClientId(clientId);
	}

	@Override
	public Collection<OAuth2AccessToken> findTokensByUserName(String userName) {
		return accessTokenStore.findTokensByUserName(userName);
	}

	@Override
	public Collection<OAuth2AccessToken> findTokensWithAuthzCode(String authzCode) {
		return accessTokenStore.findTokensWithAuthzCode(authzCode);
	}

	@Override
	public OAuth2AccessToken getAccessTokenUsingRefreshToken(String refreshToken) {
		return accessTokenStore.getAccessTokenUsingRefreshToken(refreshToken);
	}

	@Override
	public void onShutdown() {
		accessTokenStore.onShutdown();
	}

	@Override
	public OAuth2AccessToken readAccessToken(String tokenValue) {
		return accessTokenStore.readAccessToken(tokenValue);
	}

	@Override
	public OAuth2Authentication readAuthentication(String token) {
		return accessTokenStore.readAuthentication(token);
	}

	@Override
	public OAuth2Authentication readAuthenticationForRefreshToken(String value) {
		return accessTokenStore.readAuthenticationForRefreshToken(value);
	}

	@Override
	public OAuth2Authentication readAuthenticationFromToken(OAuth2AccessToken token) {
		return accessTokenStore.readAuthenticationFromToken(token);
	}

	@Override
	public OAuth2RefreshToken readRefreshToken(String tokenValue) {
		return accessTokenStore.readRefreshToken(tokenValue);
	}

	@Override
	public boolean removeAccessToken(String token) {
		return accessTokenStore.removeAccessToken(token);
	}

	@Override
	public boolean removeAccessTokenUsingAuthzCode(String authzCode) {
		return accessTokenStore.removeAccessTokenUsingAuthzCode(authzCode);
	}

	@Override
	public boolean removeAccessTokenUsingRefreshToken(String refreshToken) {
		return accessTokenStore.removeAccessTokenUsingRefreshToken(refreshToken);
	}

	@Override
	public void removeExpiredTokens() {
		accessTokenStore.removeExpiredTokens();
	}

	@Override
	public boolean removeRefreshToken(String token) {
		return accessTokenStore.removeRefreshToken(token);
	}

	@Override
	public boolean removeRefreshTokenByIndex(String tokenValueHash) {
		return accessTokenStore.removeRefreshTokenByIndex(tokenValueHash);
	}

	@Override
	public void removeTokensForApp(String applicationId) {
		accessTokenStore.removeTokensForApp(applicationId);
	}

	@Override
	public void removeTokensForAppAndSubject(String applicationId, String subjectId) {
		accessTokenStore.removeTokensForAppAndSubject(applicationId, subjectId);
	}

	@Override
	public void removeTokensForSubject(String subjectId) {
		accessTokenStore.removeTokensForSubject(subjectId);
	}

	@Override
	public boolean storeAccessToken(OAuth2AccessToken token, OAuth2Authentication authentication) {
		return accessTokenStore.storeAccessToken(token, authentication);
	}

	@Override
	public boolean storeRefreshToken(OAuth2RefreshToken refreshToken, OAuth2Authentication authentication) {
		return accessTokenStore.storeRefreshToken(refreshToken, authentication);
	}
}
