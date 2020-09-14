package com.vordel.circuit.script.context.resources;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import com.vordel.circuit.script.context.resources.KPSResource.KeyQueryBuilder;
import com.vordel.kps.ObjectExists;
import com.vordel.kps.ObjectNotFound;
import com.vordel.kps.Store;
import com.vordel.kps.Transaction;
import com.vordel.trace.Trace;

public class KPSMap extends AbstractMap<Object, Map<String, Object>> {
	private final Set<Entry<Object, Map<String, Object>>> entries;
	private final Collection<Map<String, Object>> values;
	private final Set<Object> keys;
	private final Store store;
	private final Integer ttl;

	public KPSMap(Store store, Integer ttl) {
		this.store = store;
		this.ttl = ttl;
		this.entries = new EntrySet(store, ttl);
		this.values = new ValueCollection(store, ttl);
		this.keys = new KeySet(store);
	}

	@Override
	public int size() {
		return size(store);
	}

	@Override
	public boolean isEmpty() {
		return isEmpty(store);
	}

	@Override
	public void clear() {
		values().clear();
	}

	@Override
	public Collection<Map<String, Object>> values() {
		return values;
	}

	@Override
	public Set<Object> keySet() {
		return keys;
	}

	@Override
	public boolean containsValue(Object value) {
		return values().contains(value);
	}

	@Override
	public boolean containsKey(Object key) {
		return keySet().contains(key);
	}

	@Override
	public Map<String, Object> get(Object key) {
		return get(store, key, false);
	}

	@Override
	public Map<String, Object> put(Object key, Map<String, Object> value) {
		return put(store, key, value, ttl, false);
	}

	@Override
	public Map<String, Object> remove(Object key) {
		return get(store, key, true);
	}

	@Override
	public Set<Entry<Object, Map<String, Object>>> entrySet() {
		return entries;
	}

	private static Map<String, Object> get(Store store, Object key, boolean remove) {
		Map<String, Object> previous = null;

		if ((store != null) && (key != null)) {
			try {
				previous = store.getCached(key);

				if (remove && (previous != null)) {
					Transaction transaction = store.beginTransaction();

					try {
						transaction.delete(key);
					} finally {
						transaction.close();
					}
				}
			} catch (ObjectNotFound e) {
				/* ignore */
			}
		}

		return previous;
	}

	private static Map<String, Object> put(Store store, Object key, Map<String, Object> entry, Integer ttl, boolean add) {
		Map<String, Object> previous = null;

		if (entry == null) {
			previous = get(store, key, true);
		} else if ((store != null) && (entry != null)) {
			String primaryKey = store.getPrimaryKey();

			/* clone incoming entry */
			entry = new LinkedHashMap<String, Object>(entry);

			if (key != null) {
				/* if a key is provided, override it in provided entry */
				entry.put(primaryKey, key);
			} else {
				/* no key provided, retrieve from entry */
				key = entry.get(primaryKey);
			}

			if (key != null) {
				try {
					previous = store.getCached(key);
				} catch (ObjectNotFound e) {
					/* ignore */
				}
			}

			Transaction transaction = store.beginTransaction();

			try {
				if (previous == null) {
					if (ttl == null) {
						transaction.create(entry);
					} else {
						transaction.create(entry, ttl);
					}
				} else if (add) {
					throw new IllegalStateException("an entry with this key already exists");
				} else {
					if (ttl == null) {
						transaction.update(entry);
					} else {
						transaction.update(entry, ttl);
					}
				}
			} catch (ObjectExists e) {
				throw new IllegalStateException("object should not exist", e);
			} catch (ObjectNotFound e) {
				throw new IllegalStateException("object does not exist", e);
			} finally {
				transaction.close();
			}
		}

		return previous;
	}

	private static boolean isEmpty(Store store) {
		boolean empty = true;

		if (store != null) {
			Transaction transaction = store.beginTransaction();

			try {
				/* only iterate on the first value */
				Iterator<Map<String, Object>> iterator = transaction.iterator();

				while (empty && iterator.hasNext()) {
					empty &= iterator.next() == null;
				}
			} finally {
				transaction.close();
			}
		}

		return empty;
	}

	private static int size(Store store) {
		int size = 0;

		if (store != null) {
			Transaction transaction = store.beginTransaction();

			try {
				Iterator<Map<String, Object>> iterator = transaction.iterator();

				while ((size < Integer.MAX_VALUE) && iterator.hasNext()) {
					iterator.next();

					size++;
				}
			} finally {
				transaction.close();
			}
		}

		return size;
	}

	public static class KeySet extends AbstractSet<Object> {
		private final Store store;

		public KeySet(Store store) {
			this.store = store;
		}

		@Override
		public Iterator<Object> iterator() {
			return new KeyIterator(store);
		}

		@Override
		public boolean isEmpty() {
			return KPSMap.isEmpty(store);
		}

		@Override
		public boolean contains(Object o) {
			return get(store, o, false) != null;
		}

		@Override
		public boolean remove(Object o) {
			return get(store, o, true) != null;
		}

		@Override
		public int size() {
			return KPSMap.size(store);
		}
	}

	public static class ValueCollection extends AbstractCollection<Map<String, Object>> {
		private final Store store;
		private final Integer ttl;

		public ValueCollection(Store store, Integer ttl) {
			this.store = store;
			this.ttl = ttl;
		}

		private boolean contains(Object entry, boolean remove) {
			boolean contained = false;

			if ((store != null) && (entry instanceof Map)) {
				String primaryKey = store.getPrimaryKey();
				Object key = ((Map<?, ?>) entry).get(primaryKey);

				if (key != null) {
					Map<String, Object> previous = null;
					Transaction transaction = store.beginTransaction();

					try {
						/* search for a previous entry */
						Iterable<Map<String, Object>> results = transaction.query(new KeyQueryBuilder().append(primaryKey, key).build());
						Iterator<Map<String, Object>> iterator = results.iterator();

						while ((previous == null) && iterator.hasNext()) {
							previous = iterator.next();
						}

						contained = (previous != null) && (previous.equals(entry));

						if (remove && contained) {
							transaction.delete(key);
						}
					} catch (ObjectNotFound e) {
						throw new IllegalStateException("object does not exist", e);
					} finally {
						transaction.close();
					}
				}
			}
			return contained;
		}

		@Override
		public Iterator<Map<String, Object>> iterator() {
			return new ValueIterator(store);
		}

		@Override
		public boolean contains(Object o) {
			return contains(o, false);
		}

		@Override
		public boolean remove(Object o) {
			return contains(o, true);
		}

		@Override
		public boolean add(Map<String, Object> e) {
			put(store, null, e, ttl, true);

			return true;
		}

		@Override
		public boolean isEmpty() {
			return KPSMap.isEmpty(store);
		}

		@Override
		public int size() {
			return KPSMap.size(store);
		}
	}

	public static class EntrySet extends AbstractSet<Entry<Object, Map<String, Object>>> {
		private final Store store;
		private final Integer ttl;

		public EntrySet(Store store, Integer ttl) {
			this.store = store;
			this.ttl = ttl;
		}

		@Override
		public Iterator<Entry<Object, Map<String, Object>>> iterator() {
			return new EntryIterator(store, ttl);
		}

		@Override
		public boolean isEmpty() {
			return KPSMap.isEmpty(store);
		}

		@Override
		public int size() {
			return KPSMap.size(store);
		}
	}

	public static class KeyIterator implements Iterator<Object> {
		private final Iterator<Map<String, Object>> iterator;
		private final String primaryKey;
		private final Store store;

		private Object removeKey = null;
		private Object key = null;

		public KeyIterator(Store store) {
			List<Map<String, Object>> entries = new ArrayList<Map<String, Object>>();
			String primaryKey = null;

			if (store != null) {
				Transaction transaction = store.beginTransaction();

				primaryKey = store.getPrimaryKey();

				try {
					Iterator<Map<String, Object>> iterator = transaction.iterator();

					while (iterator.hasNext()) {
						entries.add(iterator.next());
					}
				} finally {
					transaction.close();
				}
			}

			this.store = store;
			this.primaryKey = primaryKey;
			this.iterator = entries.iterator();
		}

		@Override
		public boolean hasNext() {
			if (key == null) {
				removeKey = null;

				while ((key == null) && iterator.hasNext()) {
					Map<String, Object> entry = iterator.next();

					key = entry == null ? null : entry.get(primaryKey);
				}
			}

			return key != null;
		}

		@Override
		public Object next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			try {
				removeKey = key;

				return key;
			} finally {
				key = null;
			}
		}

		@Override
		public void remove() {
			if (removeKey == null) {
				throw new IllegalStateException("no previous entry returned by next()");
			}

			iterator.remove();

			/*
			 * since at least a value has been returned, store can't be null at this point
			 */
			Transaction transaction = store.beginTransaction();

			try {
				transaction.delete(removeKey);
			} catch (ObjectNotFound e) {
				Trace.error("Can't remove object", e);
			} finally {
				transaction.close();

				removeKey = null;
			}
		}
	}

	public static class ValueIterator implements Iterator<Map<String, Object>> {
		private final Iterator<Map<String, Object>> iterator;
		private final String primaryKey;
		private final Store store;

		private Map<String, Object> entry = null;
		private Object removeKey = null;

		public ValueIterator(Store store) {
			List<Map<String, Object>> entries = new ArrayList<Map<String, Object>>();
			String primaryKey = null;

			if (store != null) {
				Transaction transaction = store.beginTransaction();

				primaryKey = store.getPrimaryKey();

				try {
					Iterator<Map<String, Object>> iterator = transaction.iterator();

					while (iterator.hasNext()) {
						entries.add(iterator.next());
					}
				} finally {
					transaction.close();
				}
			}

			this.store = store;
			this.primaryKey = primaryKey;
			this.iterator = entries.iterator();
		}

		@Override
		public boolean hasNext() {
			if (entry == null) {
				removeKey = null;

				while ((entry == null) && iterator.hasNext()) {
					entry = iterator.next();
				}
			}

			return entry != null;
		}

		@Override
		public Map<String, Object> next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			try {
				removeKey = entry.get(primaryKey);

				return entry;
			} finally {
				entry = null;
			}
		}

		@Override
		public void remove() {
			if (removeKey == null) {
				throw new IllegalStateException("no previous entry returned by next()");
			}

			iterator.remove();

			/*
			 * since at least a value has been returned, store can't be null at this point
			 */
			Transaction transaction = store.beginTransaction();

			try {
				transaction.delete(removeKey);
			} catch (ObjectNotFound e) {
				Trace.error("Can't remove object", e);
			} finally {
				transaction.close();

				removeKey = null;
			}
		}
	}

	public static class EntryIterator implements Iterator<Entry<Object, Map<String, Object>>> {
		private final Iterator<Map<String, Object>> iterator;
		private final String primaryKey;
		private final Integer ttl;
		private final Store store;

		private Entry<Object, Map<String, Object>> entry = null;
		private Object removeKey = null;

		public EntryIterator(Store store, Integer ttl) {
			List<Map<String, Object>> entries = new ArrayList<Map<String, Object>>();
			String primaryKey = null;

			if (store != null) {
				Transaction transaction = store.beginTransaction();

				primaryKey = store.getPrimaryKey();

				try {
					Iterator<Map<String, Object>> iterator = transaction.iterator();

					while (iterator.hasNext()) {
						entries.add(iterator.next());
					}
				} finally {
					transaction.close();
				}
			}

			this.store = store;
			this.ttl = ttl;
			this.primaryKey = primaryKey;
			this.iterator = entries.iterator();
		}

		@Override
		public boolean hasNext() {
			if (entry == null) {
				removeKey = null;

				while ((entry == null) && iterator.hasNext()) {
					Map<String, Object> value = iterator.next();
					Object key = value == null ? null : value.get(primaryKey);

					if (key != null) {
						entry = new KPSEntry(store, ttl, key, value);
					}
				}
			}

			return entry != null;
		}

		@Override
		public Entry<Object, Map<String, Object>> next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			try {
				removeKey = entry.getKey();

				return entry;
			} finally {
				entry = null;
			}
		}

		@Override
		public void remove() {
			if (removeKey == null) {
				throw new IllegalStateException("no previous entry returned by next()");
			}

			iterator.remove();

			/*
			 * since at least a value has been returned, store can't be null at this point
			 */
			Transaction transaction = store.beginTransaction();

			try {
				transaction.delete(removeKey);
			} catch (ObjectNotFound e) {
				Trace.error("Can't remove object", e);
			} finally {
				transaction.close();

				removeKey = null;
			}
		}

		private class KPSEntry extends SimpleEntry<Object, Map<String, Object>> {
			/**
			 * 
			 */
			private static final long serialVersionUID = -6433757390099696246L;

			private final Store store;
			private final Integer ttl;

			public KPSEntry(Store store, Integer ttl, Object key, Map<String, Object> value) {
				super(key, value);

				this.store = store;
				this.ttl = ttl;
			}

			@Override
			public Map<String, Object> setValue(Map<String, Object> value) {
				Map<String, Object> previous = null;
				Object key = getKey();

				previous = put(store, key, value, ttl, false);

				super.setValue(value);

				return previous;
			}
		}
	}
}
