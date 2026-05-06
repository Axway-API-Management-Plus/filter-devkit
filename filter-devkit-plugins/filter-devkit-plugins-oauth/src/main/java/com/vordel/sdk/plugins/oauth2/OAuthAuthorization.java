package com.vordel.sdk.plugins.oauth2;

import java.util.Collection;
import java.util.Date;
import java.util.Set;

import com.vordel.circuit.oauth.common.AuthorizationStore;
import com.vordel.circuit.oauth.common.exception.AuthorizationStorageException;

public class OAuthAuthorization {
	private final com.vordel.circuit.oauth.common.OAuthAuthorization authorization;

	public OAuthAuthorization(String applicationID, String resourceOwnerID, Set<String> scopes, Date created, String id) {
		this.authorization = new com.vordel.circuit.oauth.common.OAuthAuthorization(applicationID, resourceOwnerID, scopes, created, id);
	}
	
	private OAuthAuthorization(com.vordel.circuit.oauth.common.OAuthAuthorization authorization) {
		this.authorization = authorization;
	}

	public static OAuthAuthorization retrieveAuthorizationByAppAndSubject(AuthorizationStore authzStore, String applicationID, String authSubject) {
		com.vordel.circuit.oauth.common.OAuthAuthorization authorization = authzStore.retrieveAuthorizationByAppAndSubject(applicationID, authSubject);
		
		return authorization == null ? null : new OAuthAuthorization(authorization);
	}

	public Collection<? extends String> getScopes() {
		return authorization.getScopes();
	}

	public void setScopes(Set<String> scopes) {
		authorization.setScopes(scopes);
	}

	public void update(AuthorizationStore authzStore) throws AuthorizationStorageException {
		authzStore.update(authorization);
	}

	public void store(AuthorizationStore authzStore) throws AuthorizationStorageException {
		authzStore.store(authorization);
	}
}
