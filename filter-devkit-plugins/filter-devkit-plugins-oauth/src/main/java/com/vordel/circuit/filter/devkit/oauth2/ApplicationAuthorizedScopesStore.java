package com.vordel.circuit.filter.devkit.oauth2;

import java.util.Set;

public interface ApplicationAuthorizedScopesStore {
	Set<String> retrieveApplicationAuthorizedScopes(String applicationID);
}
