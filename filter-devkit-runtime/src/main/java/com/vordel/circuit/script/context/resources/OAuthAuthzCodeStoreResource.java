package com.vordel.circuit.script.context.resources;

import com.vordel.circuit.oauth.persistence.OauthLoadableCache;
import com.vordel.circuit.oauth.store.AuthorizationCodeStore;
import com.vordel.circuit.oauth.token.AuthorizationCode;
import com.vordel.config.ConfigContext;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;

public class OAuthAuthzCodeStoreResource implements ContextResource, AuthorizationCodeStore {
	private final AuthorizationCodeStore authZCodeStore;

	public OAuthAuthzCodeStoreResource(ConfigContext ctx, Entity entity, String reference) throws EntityStoreException {
		this(ctx, entity.getReferenceValue(reference));
	}

	public OAuthAuthzCodeStoreResource(ConfigContext ctx, ESPK reference) {
		this(getAuthzCodeStore(reference));
	}

	public OAuthAuthzCodeStoreResource(AuthorizationCodeStore authZCodeStore) {
		this.authZCodeStore = authZCodeStore;
	}

	protected static AuthorizationCodeStore getAuthzCodeStore(ESPK key) throws EntityStoreException {
		if ((key == null) || (EntityStore.ES_NULL_PK.equals(key))) {
			throw new EntityStoreException("Can not look up access token store with NULL PK");
		}
		OauthLoadableCache storesCache = OauthLoadableCache.getInstance();

		if (storesCache == null) {
			throw new EntityStoreException("OAuth stores have not been loaded yet");
		}

		AuthorizationCodeStore authzCodeStore = storesCache.getAuthzCodeStore(key);

		if (authzCodeStore == null) {
			throw new EntityStoreException("Failed to find an OAuth Authorization Code store with given entity pk", new ESPK[] {
					key });
		}

		return authzCodeStore;
	}

	@Override
	public boolean add(AuthorizationCode code) {
		return authZCodeStore.add(code);
	}

	@Override
	public boolean remove(String code) {
		return authZCodeStore.remove(code);
	}

	@Override
	public boolean remove(AuthorizationCode code) {
		return authZCodeStore.remove(code);
	}

	@Override
	public AuthorizationCode get(String code) {
		return authZCodeStore.get(code);
	}

	@Override
	public void removeExpiredCodes() {
		authZCodeStore.removeExpiredCodes();
	}

	@Override
	public void onShutdown() {
		authZCodeStore.onShutdown();
	}
}
