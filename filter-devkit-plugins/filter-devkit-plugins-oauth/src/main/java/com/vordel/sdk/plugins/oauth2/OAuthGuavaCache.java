package com.vordel.sdk.plugins.oauth2;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.vordel.apiportal.config.PortalConfiguration;
import com.vordel.circuit.Message;
import com.vordel.circuit.oauth.common.OAuth2Utils;
import com.vordel.circuit.oauth.kps.ApplicationDetails;
import com.vordel.common.apiserver.controller.StoreAccess;
import com.vordel.common.apiserver.controller.BaseOAuthController;
import com.vordel.common.apiserver.controller.BaseOAuthResourceController;
import com.vordel.common.apiserver.controller.IStoreAccess;
import com.vordel.common.apiserver.discovery.model.OAuthAppScope;
import com.vordel.common.apiserver.model.OAuthClient;
import com.vordel.kps.ObjectNotFound;
import com.vordel.trace.Trace;

public class OAuthGuavaCache {
	public static final PortalConfiguration PORTAL_CONFIG;

	private static final Cache<String, CacheValueHolder<ApplicationDetails>> DETAILS_CACHE;
	private static final Cache<String, CacheValueHolder<OAuthClient>> CLIENT_CACHE;
	private static final Cache<String, CacheValueHolder<List<OAuthAppScope>>> APPSCOPES_CACHE;

	private static final String DETAILS_PROPERTY;
	private static final String SCOPES_PROPERTY;

	static {
		DETAILS_PROPERTY = "oauth.client.details";
		SCOPES_PROPERTY = "oauth.client.appscopes";

		long CACHE_TTL = 3000L;
		long CACHE_SIZE = 1000L;

		PORTAL_CONFIG = PortalConfiguration.getInstance();

		CLIENT_CACHE = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterWrite(CACHE_TTL, TimeUnit.MILLISECONDS).build();
		DETAILS_CACHE = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterWrite(CACHE_TTL, TimeUnit.MILLISECONDS).build();
		APPSCOPES_CACHE = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterWrite(CACHE_TTL, TimeUnit.MILLISECONDS).build();
	}

	private static void processExecutionException(Exception e) {
		Throwable cause = e.getCause();

		if (cause instanceof RuntimeException) {
			throw (RuntimeException) cause;
		} else if (cause instanceof Error) {
			throw (Error) cause;
		} else if (cause != null){
			Trace.error("unexpected error", cause);
		}
	}

	public static List<OAuthAppScope> getScopesListFromApp(final String applicationId) {
		List<OAuthAppScope> scopes = null;

		try {
			scopes = APPSCOPES_CACHE.get(applicationId, new Callable<CacheValueHolder<List<OAuthAppScope>>>() {
				@Override
				public CacheValueHolder<List<OAuthAppScope>> call() {
					IStoreAccess storeAccess = StoreAccess.internalWithRole("admin");
					BaseOAuthResourceController controller = storeAccess.getOAuthResourceController();
					List<OAuthAppScope> scopes = controller.getScopes(applicationId);

					return new CacheValueHolder<List<OAuthAppScope>>(scopes);
				}
			}).call();
		} catch (ExecutionException e) {
			processExecutionException(e);
		}

		return scopes;
	}

	public static List<OAuthAppScope> getScopesListFromClientId(Message m, String client_id) {
		ApplicationDetails details = getAppDetailsFromClientId(m, client_id);
		List<OAuthAppScope> scopes = null;

		if (details != null) {
			try {
				scopes = details == null ? null : getScopesListFromApp(details.getApplicationID());
			} finally {
				if (scopes == null) {
					m.remove(SCOPES_PROPERTY);
				} else {
					m.put(SCOPES_PROPERTY, scopes);
				}
			}
		}

		return scopes;
	}

	public static OAuthClient getOAuthClient(String client_id) {
		OAuthClient client = null;

		if (client_id != null) {
			try {
				client = CLIENT_CACHE.get(client_id, new Callable<CacheValueHolder<OAuthClient>>() {
					@Override
					public CacheValueHolder<OAuthClient> call() throws Exception {
						IStoreAccess storeAccess = StoreAccess.internalWithRole("admin");
						BaseOAuthController controller = storeAccess.getOAuthController();
						OAuthClient client = null;

						try {
							client = controller.internalGetOAuthClient(client_id);
						} catch(ObjectNotFound e) {
							/* ignore */
						}

						return new CacheValueHolder<OAuthClient>(client);
					}
				}).call();
			} catch (ExecutionException e) {
				processExecutionException(e);
			}
		}

		return client;
	}

	public static ApplicationDetails getAppDetailsFromClientId(final Message m, final String client_id) {
		ApplicationDetails details = null;

		if (client_id != null) {
			try {
				details = DETAILS_CACHE.get(client_id, new Callable<CacheValueHolder<ApplicationDetails>>() {
					@Override
					public CacheValueHolder<ApplicationDetails> call() throws Exception {
						ApplicationDetails details = (ApplicationDetails) m.get(DETAILS_PROPERTY);

						if (details != null) {
							if (!client_id.equals(details.getClientID())) {
								/* existing details in message do not match provided client_id */
								details = null;
							} else {
								Boolean enabled = details.isEnabled();

								if ((enabled == null) || (!enabled.booleanValue())) {
									/* existing details in message are not enabled */
									details = null;
								}
							}
						}

						if (details == null) {
							details = OAuth2Utils.getAppDetailsFromClientId(m, client_id);
						}

						return new CacheValueHolder<ApplicationDetails>(details);
					}
				}).call();
			} catch (ExecutionException e) {
				processExecutionException(e);
			}
		}

		return details;
	}

	private static class CacheValueHolder<T> implements Callable<T> {
		private final T value;

		public CacheValueHolder(T value) {
			this.value = value;
		}

		@Override
		public T call() {
			return value;
		}
	}
}
