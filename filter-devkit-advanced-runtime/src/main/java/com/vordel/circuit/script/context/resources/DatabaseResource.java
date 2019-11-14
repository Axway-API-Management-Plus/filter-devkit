package com.vordel.circuit.script.context.resources;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.vordel.common.crypto.PasswordCipher;
import com.vordel.common.db.DbConnection;
import com.vordel.common.db.DbConnectionCache;
import com.vordel.config.ConfigContext;
import com.vordel.es.ESPK;
import com.vordel.es.EntityStore;
import com.vordel.trace.Trace;

public class DatabaseResource implements ContextResource, Closeable {
	private final Object SYNC = new Object();

	private DbConnection externalDb = null;

	public DatabaseResource(String connectionName) {
		this.externalDb = DbConnectionCache.getConnection(connectionName);
	}

	public DatabaseResource(ConfigContext ctx, ESPK dbConnPK) {
		this(ctx.getStore(), dbConnPK, ctx.getCipher());
	}

	private DatabaseResource(EntityStore store, ESPK dbConnPK, PasswordCipher cipher) {
		this.externalDb = DbConnectionCache.getConnection(store, dbConnPK, cipher);
	}

	public final String getJdbcDriver() {
		synchronized (SYNC) {
			if (externalDb == null) {
				throw new IllegalStateException("Database is closed");
			}

			return externalDb.getJdbcDriver();
		}
	}

	public final Connection getConnection() throws SQLException {
		synchronized (SYNC) {
			if (externalDb == null) {
				throw new IllegalStateException("Database is closed");
			}

			return externalDb.getConnection();
		}
	}

	public final String getDatabaseConnectionName() {
		synchronized (SYNC) {
			if (externalDb == null) {
				throw new IllegalStateException("Database is closed");
			}

			return externalDb.getDatabaseConnectionName();
		}
	}

	public final DataSource getDataSource() {
		synchronized (SYNC) {
			if (externalDb == null) {
				throw new IllegalStateException("Database is closed");
			}

			return externalDb.getDataSource();
		}
	}

	public final boolean isTimezoneAware() {
		synchronized (SYNC) {
			if (externalDb == null) {
				throw new IllegalStateException("Database is closed");
			}

			return externalDb.isTimezoneAware();
		}
	}

	@Override
	public final void close() {
		synchronized (SYNC) {
			if (externalDb != null) {
				try {
					externalDb.close();
				} catch (SQLException e) {
					Trace.error("Unable to close database", e);
				} finally {
					externalDb = null;
				}
			}
		}
	}
}
