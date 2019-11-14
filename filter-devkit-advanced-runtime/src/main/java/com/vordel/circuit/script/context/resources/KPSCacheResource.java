package com.vordel.circuit.script.context.resources;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.script.ScriptHelper;
import com.vordel.circuit.script.context.resources.KPSResource.KeyQueryBuilder;
import com.vordel.kps.ObjectExists;
import com.vordel.kps.ObjectNotFound;
import com.vordel.kps.Store;
import com.vordel.kps.Transaction;
import com.vordel.trace.Trace;

public class KPSCacheResource extends CacheResource {
	private final Store store;

	public KPSCacheResource(KPSResource resource) {
		this(resource == null ? null : resource.getStore());
	}

	public KPSCacheResource(Store resource) {
		this.store = resource;
	}

	private boolean isFieldInCache(String field, Object value) throws CircuitAbortException {
		boolean contained = false;

		if ((store != null) && (value != null)) {
			Transaction transaction = store.beginTransaction();

			try {
				KeyQueryBuilder builder = new KeyQueryBuilder();

				builder.append(field, ScriptHelper.asSerializable(value));

				Iterable<Map<String, Object>> results = transaction.query(builder.build());

				contained = results.iterator().hasNext();
			} catch (IOException e) {
				throw new CircuitAbortException(e);
			} finally {
				transaction.close();
			}
		}

		return contained;
	}

	private Iterator<?> fieldIterator(final String field) {
		return new Iterator<Object>() {
			private final Iterator<Map<String, Object>> iterator = new KPSMap.ValueIterator(store);

			private boolean remove = false;
			private Object value = null;

			@Override
			public boolean hasNext() {
				if (value == null) {
					remove = false;

					try {
						while ((value == null) && iterator.hasNext()) {
							Map<String, Object> entry = iterator.next();

							if (entry != null) {
								value = entry.get(field);

								if (value instanceof String) {
									value = ScriptHelper.asObject((String) value);
								} else {
									value = null;
								}
							}
						}
					} catch (IOException e) {
						Trace.error("unable to decode value", e);
					}
				}

				return value != null;
			}

			@Override
			public Object next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}

				try {
					remove = true;

					return value;
				} finally {
					value = null;
				}
			}

			@Override
			public void remove() {
				if (!remove) {
					throw new IllegalStateException("no previous entry returned by next()");
				}

				remove = false;
				iterator.remove();
			}
		};
	}

	@Override
	public boolean isKeyInCache(Object key) throws CircuitAbortException {
		return isFieldInCache("cache_key", key);
	}

	@Override
	public boolean isValueInCache(Object value) throws CircuitAbortException {
		return isFieldInCache("cache_value", value);
	}

	@Override
	public Object putCachedValue(Object key, Object value, int ttl) throws CircuitAbortException {
		Object previous = null;

		if ((store != null) && (key != null)) {
			Transaction transaction = store.beginTransaction();
			try {
				KeyQueryBuilder builder = new KeyQueryBuilder();

				builder.append("cache_key", key = ScriptHelper.asSerializable(key));

				Iterable<Map<String, Object>> results = transaction.query(builder.build());
				List<Map<String, Object>> duplicate = new ArrayList<Map<String, Object>>();
				Iterator<Map<String, Object>> iterator = results.iterator();
				Map<String, Object> entry = null;

				if (iterator.hasNext()) {
					entry = iterator.next();
					previous = entry.get("cache_value");

					if (previous instanceof String) {
						previous = ScriptHelper.asObject((String) previous);
					}
				}

				if ((value == null) || (ttl < 0)) {
					if (entry != null) {
						duplicate.add(entry);
					}
				} else {
					Serializable serializable = ScriptHelper.asSerializable(value);

					if (entry == null) {
						entry = new HashMap<String, Object>();

						entry.put("cache_key", key);
						entry.put("cache_value", serializable);

						if (ttl > 0) {
							transaction.create(entry, ttl);
						} else if (ttl == 0) {
							transaction.create(entry);
						}
					} else {
						entry.put("cache_value", serializable);

						if (ttl > 0) {
							transaction.update(entry, ttl);
						} else if (ttl == 0) {
							transaction.update(entry);
						}
					}
				}

				while (iterator.hasNext()) {
					duplicate.add(iterator.next());
				}

				if (!duplicate.isEmpty()) {
					transaction.delete(duplicate);
				}
			} catch (IOException e) {
				throw new CircuitAbortException(e);
			} catch (ObjectExists e) {
				/* should not occur */
				throw new CircuitAbortException(e);
			} catch (ObjectNotFound e) {
				/* should not occur */
				throw new CircuitAbortException(e);
			} finally {
				transaction.close();
			}
		}

		return previous;
	}

	private Object getCachedValue(Object key, boolean remove) throws CircuitAbortException {
		Object previous = null;

		if ((store != null) && (key != null)) {
			Transaction transaction = store.beginTransaction();

			try {
				KeyQueryBuilder builder = new KeyQueryBuilder();

				builder.append("cache_key", key = ScriptHelper.asSerializable(key));

				Iterable<Map<String, Object>> results = transaction.query(builder.build());
				Iterator<Map<String, Object>> iterator = results.iterator();
				Map<String, Object> entry = null;

				if (iterator.hasNext()) {
					entry = iterator.next();
					previous = entry.get("cache_value");

					if (previous instanceof String) {
						previous = ScriptHelper.asObject((String) previous);
					}
				}

				if ((previous != null) && remove) {
					List<Map<String, Object>> duplicate = new ArrayList<Map<String, Object>>();

					duplicate.add(entry);

					while (iterator.hasNext()) {
						entry = iterator.next();

						if (entry != null) {
							duplicate.add(entry);
						}

						transaction.delete(duplicate);
					}
				}

			} catch (IOException e) {
				throw new CircuitAbortException(e);
			} finally {
				transaction.close();
			}
		}

		return previous;
	}

	@Override
	public Object getCachedValue(Object key) throws CircuitAbortException {
		return getCachedValue(key, false);
	}

	@Override
	public boolean removeCacheEntry(Object key) throws CircuitAbortException {
		return getCachedValue(key, true) != null;
	}

	@Override
	public int getCacheSize() throws CircuitAbortException {
		Iterator<?> iterator = keys();
		int size = 0;

		while ((size < Integer.MAX_VALUE) && iterator.hasNext()) {
			iterator.next();

			size++;
		}

		return size;
	}

	@Override
	public Iterator<?> keys() throws CircuitAbortException {
		return fieldIterator("cache_key");
	}

	@Override
	public Iterator<?> values() throws CircuitAbortException {
		return fieldIterator("cache_value");
	}

	@Override
	protected Iterator<Entry<Object, Object>> entries() throws CircuitAbortException {
		return new Iterator<Entry<Object, Object>>() {
			private final Iterator<Map<String, Object>> iterator = new KPSMap.ValueIterator(store);

			private Entry<Object, Object> entry = null;
			private boolean remove = false;

			@Override
			public boolean hasNext() {
				if (entry == null) {
					remove = false;

					try {
						while ((entry == null) && iterator.hasNext()) {
							Map<String, Object> row = iterator.next();

							if (row != null) {
								Object key = row.get("cache_key");

								if (key instanceof String) {
									key = ScriptHelper.asObject((String) key);
								} else {
									key = null;
								}

								if (key != null) {
									Object value = row.get("cache_value");

									if (value instanceof String) {
										value = ScriptHelper.asObject((String) value);
									} else {
										value = null;
									}

									if (value != null) {
										entry = new CacheEntry(KPSCacheResource.this, key, value);
									}
								}
							}
						}
					} catch (IOException e) {
						Trace.error("unable to decode value", e);
					}
				}

				return entry != null;
			}

			@Override
			public Entry<Object, Object> next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}

				try {
					remove = true;

					return entry;
				} finally {
					entry = null;
				}
			}

			@Override
			public void remove() {
				if (!remove) {
					throw new IllegalStateException("no previous entry returned by next()");
				}

				remove = false;
				iterator.remove();
			}
		};
	}
}
