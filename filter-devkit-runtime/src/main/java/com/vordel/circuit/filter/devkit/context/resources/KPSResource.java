package com.vordel.circuit.filter.devkit.context.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.vordel.kps.ObjectExists;
import com.vordel.kps.ObjectNotFound;
import com.vordel.kps.Store;
import com.vordel.kps.Transaction;
import com.vordel.kps.query.KeyQuery;

public abstract class KPSResource implements ContextResource, ViewableResource {
	public abstract Store getStore();

	public String getPrimaryKey() {
		Store store = getStore();

		return store.getPrimaryKey();
	}

	public List<String> getReadKey() {
		Store store = getStore();

		return store.getReadKey();
	}

	public Map<String, Object> getCached(KeyQuery query) throws ObjectNotFound {
		Store store = getStore();

		return store.getCached(query);
	}

	public Map<String, Object> getCached(Object id) throws ObjectNotFound {
		Store store = getStore();

		return store.getCached(id);
	}

	public Map<String, Object> getEntry(Object key) throws ObjectNotFound {
		Store store = getStore();
		Transaction transaction = store.beginTransaction();
		Map<String, Object> entry = null;
		try {
			entry = transaction.get(key);
		} finally {
			transaction.close();
		}

		return entry;
	}

	public Map<String, Object> createEntry(Map<String, Object> entry) throws ObjectExists {
		Store store = getStore();
		Transaction transaction = store.beginTransaction();

		try {
			entry = transaction.create(entry);
		} finally {
			transaction.close();
		}

		return entry;
	}

	public Map<String, Object> createEntry(Map<String, Object> entry, int ttl) throws ObjectExists {
		Store store = getStore();
		Transaction transaction = store.beginTransaction();

		try {
			entry = transaction.create(entry, ttl);
		} finally {
			transaction.close();
		}

		return entry;
	}

	public Map<String, Object> updateEntry(Map<String, Object> entry) throws ObjectNotFound, ObjectExists {
		Store store = getStore();
		Transaction transaction = store.beginTransaction();

		try {
			entry = transaction.update(entry);
		} finally {
			transaction.close();
		}

		return entry;
	}

	public Map<String, Object> updateEntry(Map<String, Object> entry, int ttl) throws ObjectNotFound, ObjectExists {
		Store store = getStore();
		Transaction transaction = store.beginTransaction();

		try {
			entry = transaction.update(entry, ttl);
		} finally {
			transaction.close();
		}

		return entry;
	}

	public void removeEntry(Object key) throws ObjectNotFound {
		Store store = getStore();
		Transaction transaction = store.beginTransaction();

		try {
			transaction.delete(key);
		} finally {
			transaction.close();
		}
	}

	@Override
	public KPSDictionaryView getResourceView() {
		/* return the Selector special view object */
		return new KPSDictionaryView(this);
	}

	public static class KeyQueryBuilder {
		private final List<Pair<String, Object>> pairs = new ArrayList<Pair<String, Object>>();

		public KeyQueryBuilder append(String key, Object value) {
			pairs.add(Pair.of(key, value));

			return this;
		}

		public KeyQuery build() {
			return new KeyQuery(pairs);
		}
	}
}
