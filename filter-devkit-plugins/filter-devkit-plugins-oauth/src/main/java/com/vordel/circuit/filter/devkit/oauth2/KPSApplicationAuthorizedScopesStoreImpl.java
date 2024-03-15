package com.vordel.circuit.filter.devkit.oauth2;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.vordel.circuit.filter.devkit.context.resources.KPSStoreResource;
import com.vordel.kps.ObjectNotFound;
import com.vordel.kps.Store;

public class KPSApplicationAuthorizedScopesStoreImpl implements ApplicationAuthorizedScopesStore {
	private static final ApplicationAuthorizedScopesStore INSTANCE = getDefaultApplicationAuthorizedScopesStore();

	public static ApplicationAuthorizedScopesStore getInstance() {
		return INSTANCE;
	}

	private final Store store;

	private static ApplicationAuthorizedScopesStore getDefaultApplicationAuthorizedScopesStore() {
		Store store = KPSStoreResource.getStoreByAlias("OAuthAuthorizationExceptions");
		
		return new KPSApplicationAuthorizedScopesStoreImpl(store);
	}
	
	public KPSApplicationAuthorizedScopesStoreImpl(Store store) {
		this.store = store;
	}
	
	@Override
	public Set<String> retrieveApplicationAuthorizedScopes(String applicationID) {
		Set<String> result = new HashSet<String>();

		if (store != null) {
			try {
				Map<String, Object> object = store.getCached(applicationID);

				if (object != null) {
					Collection<?> scopes = (Collection<?>) object.get("scopes");

					if (scopes != null) {
						scopes.forEach((scope) -> {
							if (scope instanceof String) {
								result.add((String) scope);
							}
						});
					}
				}
			} catch (ObjectNotFound e) {
				// ignore
			}
		}

		return result;
	}
}
