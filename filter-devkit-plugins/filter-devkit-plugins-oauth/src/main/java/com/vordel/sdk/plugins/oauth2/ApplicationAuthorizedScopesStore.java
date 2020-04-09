package com.vordel.sdk.plugins.oauth2;

import java.util.Set;

public interface ApplicationAuthorizedScopesStore {
	Set<String> retrieveApplicationAuthorizedScopes(String applicationID);
}
