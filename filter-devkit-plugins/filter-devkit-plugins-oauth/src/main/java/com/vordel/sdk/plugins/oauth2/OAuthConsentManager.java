package com.vordel.sdk.plugins.oauth2;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.oauth.common.AuthorizationStore;
import com.vordel.circuit.oauth.common.KPSAuthorizationStoreImpl;
import com.vordel.circuit.oauth.common.exception.AuthorizationStorageException;
import com.vordel.circuit.oauth.kps.ApplicationDetails;
import com.vordel.circuit.oauth.store.TokenStore;
import com.vordel.kps.impl.KPS;
import com.vordel.trace.Trace;

public class OAuthConsentManager {
	private final ApplicationAuthorizedScopesStore appsAuthzStore;
	private final AuthorizationStore authzStore;
	private final boolean acceptRequestScopes;

	private final Set<String> scopesPreAuthorisedByApplication = new HashSet<String>();
	private final Set<String> scopesNewlyApprovedByOwner = new HashSet<String>();
	private final Set<String> scopesFromOwnerRequestIntersect = new HashSet<String>();
	private final Set<String> scopesForAuthorisation = new HashSet<String>();
	private Set<String> scopesForToken;

	public Set<String> getScopesNewlyApprovedByOwner() {
		return scopesNewlyApprovedByOwner;
	}

	public Set<String> getScopesFromOwnerRequestIntersect() {
		return scopesFromOwnerRequestIntersect;
	}

	public Set<String> getScopesForAuthorisation() {
		return scopesForAuthorisation;
	}

	public OAuthConsentManager(TokenStore tokenStore, boolean acceptRequestScopes) {
		this(KPSApplicationAuthorizedScopesStoreImpl.getInstance(), getDefaultAuthorizationStore(tokenStore), acceptRequestScopes);
	}

	public OAuthConsentManager(ApplicationAuthorizedScopesStore appsAuthzStore, AuthorizationStore authzStore, boolean acceptRequestScopes) {
		this.appsAuthzStore = appsAuthzStore;
		this.authzStore = authzStore;
		this.acceptRequestScopes = acceptRequestScopes;
	}

	private static final AuthorizationStore getDefaultAuthorizationStore(TokenStore tokenStore) {
		List<TokenStore> tokenStores = null;

		if (tokenStore != null) {
			tokenStores = Collections.singletonList(tokenStore);
		} else {
			tokenStores = Collections.emptyList();
		}

		return new KPSAuthorizationStoreImpl(KPS.getInstance().getStore("OAuthAuthorizations"), tokenStores);
	}

	public void persistNewlyApprovedScopes(String authSubject, ApplicationDetails app, OAuthAuthorization authorization, Set<String> preAuthorisedScopes, Set<?> nonPersistentScopes) throws CircuitAbortException {
		try {
			Set<String> authorizedScopes = new HashSet<String>();

			CollectionUtils.addAll(authorizedScopes, preAuthorisedScopes.iterator());
			CollectionUtils.addAll(authorizedScopes, scopesFromOwnerRequestIntersect.iterator());
			CollectionUtils.addAll(authorizedScopes, scopesNewlyApprovedByOwner.iterator());

			/* remove scopes which must not be persisted */
			authorizedScopes.removeAll(nonPersistentScopes);

			if (authorization != null) {
				authorization.setScopes(authorizedScopes);
				
				authorization.update(authzStore);
			} else if (!authorizedScopes.isEmpty()) {
				authorization = new OAuthAuthorization(app.getApplicationID(), authSubject, authorizedScopes, new Date(System.currentTimeMillis()), (String) null);
				
				authorization.store(authzStore);
			}
		} catch (AuthorizationStorageException e) {
			Trace.error(e);
			
			throw new CircuitAbortException("Unable to store authorizations.", e);
		}
	}

	public boolean scopesNeedOwnerAuthorization(Message msg, String authSubject, ApplicationDetails app, Set<String> requestedScopes) throws CircuitAbortException {
		return scopesNeedOwnerAuthorization(msg, authSubject, app, requestedScopes, Collections.emptySet(), Collections.emptySet());
	}

	public boolean scopesNeedOwnerAuthorization(Message msg, String authSubject, ApplicationDetails app, Set<String> requestedScopes, Set<?> persistentApprovedScopes, Set<?> transientApprovedScopes) throws CircuitAbortException {
		OAuthAuthorization authorization = OAuthAuthorization.retrieveAuthorizationByAppAndSubject(authzStore, app.getApplicationID(), authSubject);
		Set<String> authzExceptionsScopes = appsAuthzStore.retrieveApplicationAuthorizedScopes(app.getApplicationID());

		Set<String> scopesPreAuthorisedByOwner = new HashSet<String>();
		Set<String> preAuthorisedScopes = new HashSet<String>();

		scopesPreAuthorisedByApplication.clear();

		if (authorization != null) {
			scopesPreAuthorisedByOwner.addAll(authorization.getScopes());
		}

		if (authzExceptionsScopes != null) {
			scopesPreAuthorisedByApplication.addAll(authzExceptionsScopes);
		}

		preAuthorisedScopes.addAll(scopesPreAuthorisedByOwner);
		preAuthorisedScopes.addAll(scopesPreAuthorisedByApplication);
		scopesPreAuthorisedByApplication.removeAll(scopesPreAuthorisedByOwner);

		if (Trace.isDebugEnabled()) {
			Trace.debug(String.format("Scopes Requested by the Client App are : %s", requestedScopes));
			Trace.debug(String.format("Scopes pre-authorized by the ResourceOwner are : %s", scopesPreAuthorisedByOwner));
			Trace.debug(String.format("Scopes pre-authorized for the Application are : %s", scopesPreAuthorisedByApplication));
		}

		scopesFromOwnerRequestIntersect.addAll(preAuthorisedScopes);
		scopesFromOwnerRequestIntersect.retainAll(requestedScopes);

		scopesForAuthorisation.addAll(requestedScopes);
		scopesForAuthorisation.removeAll(preAuthorisedScopes);

		for(Object item : persistentApprovedScopes) {
			if (item instanceof String) {
				scopesNewlyApprovedByOwner.add((String) item);
			}
		}

		for(Object item : transientApprovedScopes) {
			if (item instanceof String) {
				scopesNewlyApprovedByOwner.add((String) item);
			}
		}

		scopesNewlyApprovedByOwner.removeAll(preAuthorisedScopes);
		scopesForAuthorisation.removeAll(scopesNewlyApprovedByOwner);

		if (!scopesNewlyApprovedByOwner.isEmpty()) {
			persistNewlyApprovedScopes(authSubject, app, authorization, scopesPreAuthorisedByOwner, transientApprovedScopes);
		}

		boolean result = true;

		if (acceptRequestScopes) {
			Trace.debug(String.format("Scopes automatically accepted are : %s", scopesForAuthorisation));

			scopesForAuthorisation.clear();
		}

		if (scopesForAuthorisation.isEmpty()) {
			result = false;

			scopesNewlyApprovedByOwner.addAll(scopesFromOwnerRequestIntersect);
			setScopesForToken(scopesNewlyApprovedByOwner);

			Trace.debug(String.format("Newly approved scopes by the ResourceOwner are : %s", scopesNewlyApprovedByOwner));
		} else {
			Trace.debug(String.format("Scopes for display for approval are : %s", scopesForAuthorisation));
		}

		return result;
	}

	public Set<String> getScopesForToken() {
		return scopesForToken;
	}

	public void setScopesForToken(Set<String> scopesForToken) {
		this.scopesForToken = scopesForToken;
	}
}