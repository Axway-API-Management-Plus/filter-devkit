package com.vordel.sdk.plugins.oauth2;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Date;
import java.util.Set;

import com.vordel.circuit.oauth.common.AuthorizationStore;
import com.vordel.circuit.oauth.common.exception.AuthorizationStorageException;

public class OAuthAuthorization {
	private static final Class<?> AUTHORIZATION_CLASS;

	private static final Constructor<?> AUTHORIZATION_CONSTRUCTOR;

	private static final Method AUTHORIZATION_GETSCOPES;

	private static final Method AUTHORIZATION_SETSCOPES;

	private static final Method STORE_RETRIEVEBYAPPANDSUBJECT;

	private static final Method STORE_STORE;

	private static final Method STORE_UPDATE;

	static {
		AUTHORIZATION_CLASS = getOAuthAuthorizationClass();

		try {
			AUTHORIZATION_CONSTRUCTOR = AUTHORIZATION_CLASS.getDeclaredConstructor(String.class, String.class, Set.class, Date.class, String.class);
			AUTHORIZATION_GETSCOPES = AUTHORIZATION_CLASS.getMethod("getScopes");
			AUTHORIZATION_SETSCOPES = AUTHORIZATION_CLASS.getMethod("setScopes", Set.class);
			
			STORE_RETRIEVEBYAPPANDSUBJECT = AuthorizationStore.class.getMethod("retrieveAuthorizationByAppAndSubject", String.class, String.class);
			STORE_STORE = AuthorizationStore.class.getMethod("store", AUTHORIZATION_CLASS);
			STORE_UPDATE = AuthorizationStore.class.getMethod("update", AUTHORIZATION_CLASS);
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException("Unable to retrieve a compatible class for OAuth Authorization", e);
		}
	}

	private static Class<?> getOAuthAuthorizationClass() {
		try {
			return Class.forName("com.vordel.circuit.oauth.common.Authorization");
		} catch (ClassNotFoundException e) {
			/* ignore */
		}

		try {
			return Class.forName("com.vordel.circuit.oauth.common.OAuthAuthorization");
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("Unable to retrieve a compatible class for OAuth Authorization", e);
		}
	}
	
	@SuppressWarnings("unchecked")
	private static <T> T invoke(Method method, Object instance, Object ...params) {
		try {
			return (T) method.invoke(instance, params);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			
			if (cause instanceof Error) {
				throw (Error) cause;
			} else if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			} else {
				throw new IllegalStateException(cause);
			}
		}
	}

	private final Object authorization;

	public OAuthAuthorization(String applicationID, String resourceOwnerID, Set<String> scopes, Date created, String id) {
		try {
			this.authorization = AUTHORIZATION_CONSTRUCTOR.newInstance(applicationID, resourceOwnerID, scopes, created, id);
		} catch (InstantiationException e) {
			throw new IllegalStateException(e);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			
			if (cause instanceof Error) {
				throw (Error) cause;
			} else if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			} else {
				throw new IllegalStateException(cause);
			}
		}
	}
	
	private OAuthAuthorization(Object authorization) {
		this.authorization = authorization;
	}

	public static OAuthAuthorization retrieveAuthorizationByAppAndSubject(AuthorizationStore authzStore, String applicationID, String authSubject) {
		return new OAuthAuthorization(invoke(STORE_RETRIEVEBYAPPANDSUBJECT, authzStore, applicationID, authSubject));
	}

	public Collection<? extends String> getScopes() {
		return invoke(AUTHORIZATION_GETSCOPES, authorization);
	}

	public void setScopes(Set<String> scopes) {
		invoke(AUTHORIZATION_SETSCOPES, authorization, scopes);
	}

	public void update(AuthorizationStore authzStore) throws AuthorizationStorageException {
		try {
			STORE_UPDATE.invoke(authzStore, authorization);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			
			if (cause instanceof Error) {
				throw (Error) cause;
			} else if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			} else if (cause instanceof AuthorizationStorageException) {
				throw (AuthorizationStorageException) cause;
			} else {
				throw new IllegalStateException(cause);
			}
		}
	}

	public void store(AuthorizationStore authzStore) throws AuthorizationStorageException {
		try {
			STORE_STORE.invoke(authzStore, authorization);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			
			if (cause instanceof Error) {
				throw (Error) cause;
			} else if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			} else if (cause instanceof AuthorizationStorageException) {
				throw (AuthorizationStorageException) cause;
			} else {
				throw new IllegalStateException(cause);
			}
		}
	}

}
