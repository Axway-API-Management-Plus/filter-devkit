package com.vordel.circuit.script.context.resources;

import java.io.Closeable;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;

import com.vordel.common.crypto.PasswordCipher;
import com.vordel.common.ldap.ContextCache;
import com.vordel.common.ldap.InContext;
import com.vordel.common.ldap.LdapLookup;
import com.vordel.config.ConfigContext;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;
import com.vordel.security.cert.PersonalInfo;

public class LDAPResource implements ContextResource, Closeable {
	private final Object SYNC = new Object();
	private ContextCache contextCache;

	public LDAPResource(ConfigContext ctx, ESPK ldapPK) {
		this(ctx.getStore(), ldapPK, ctx.getCipher());
	}

	private LDAPResource(EntityStore store, ESPK ldapPK, PasswordCipher cipher) {
		this(store, store.getEntity(ldapPK), cipher, null);
	}

	public LDAPResource(EntityStore es, Entity e, PasswordCipher cipher, PersonalInfo personalInfo) throws EntityStoreException {
		this.contextCache = LdapLookup.makeParams(es, e, cipher, personalInfo);
	}

	public final <T> T apply(final Function<DirContext, T> function) throws NamingException {
		synchronized (SYNC) {
			if (contextCache == null) {
				throw new IllegalStateException("LDAP Context is closed");
			}

			return new InContext<T>(contextCache) {
				@Override
				public T invoke(DirContext context) throws NamingException {
					return function.apply(context);
				}
			}.run();
		}
	}

	public final void accept(final Consumer<DirContext> consumer) throws NamingException {
		synchronized (SYNC) {
			if (contextCache == null) {
				throw new IllegalStateException("LDAP Context is closed");
			}

			new InContext<Void>(contextCache) {
				@Override
				public Void invoke(DirContext context) throws NamingException {
					consumer.accept(context);

					return null;
				}
			}.run();
		}
	}

	@Override
	public final void close() {
		synchronized (SYNC) {
			if (contextCache != null) {
				try {
					contextCache.releaseAll();
				} finally {
					contextCache = null;
				}
			}
		}
	}
}
