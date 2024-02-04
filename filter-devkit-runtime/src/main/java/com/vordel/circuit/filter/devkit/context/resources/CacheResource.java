package com.vordel.circuit.filter.devkit.context.resources;

import java.util.AbstractMap.SimpleEntry;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.trace.Trace;

/**
 * Resource representing a cache. The map interface is implemented so it can be
 * used in JUEL expressions however most methods of the map interface should be
 * avoided for performance reasons
 * 
 * @author rdesaintleger@axway.com
 */
public abstract class CacheResource implements ContextResource, ViewableResource {
	/**
	 * Check if the given key is contained into the cache
	 * 
	 * @param key
	 *            key to be checked. should be serializable depending on the
	 *            underlying implementation
	 * @return 'true' if a mapping exists for this key. 'false' otherwise.
	 * @throws CircuitAbortException
	 *             If a cache error occurs.
	 */
	public abstract boolean isKeyInCache(Object key) throws CircuitAbortException;

	/**
	 * Check if a value is contained into the cache
	 * 
	 * @param value
	 *            value to be checked. should be serializable depending on the
	 *            underlying implementation
	 * @return 'true' if a mapping exists containing this value. 'false' otherwise.
	 * @throws CircuitAbortException is unable to query cache
	 */
	public abstract boolean isValueInCache(Object value) throws CircuitAbortException;

	/**
	 * set a key/value mapping in the underlying cache using the default cache TTL,
	 * ignoring preexisting value if any
	 * 
	 * @param key
	 *            cache key. should be serializable depending on the underlying
	 *            implementation
	 * @param serializable
	 *            value to be set. should be serializable depending on the
	 *            underlying implementation
	 * @throws CircuitAbortException
	 *             If a cache error occurs.
	 */
	public void setCachedValue(Object key, Object serializable) throws CircuitAbortException {
		putCachedValue(key, serializable);
	}

	/**
	 * set a key/value mapping in the underlying cache using a specific TTL,
	 * ignoring preexisting value if any
	 * 
	 * @param key
	 *            cache key. should be serializable depending on the underlying
	 *            implementation
	 * @param serializable
	 *            value to be set. should be serializable depending on the
	 *            underlying implementation
	 * @param ttl
	 *            overridden TTL for this mapping
	 * @throws CircuitAbortException
	 *             If a cache error occurs.
	 */
	public void setCachedValue(Object key, Object serializable, int ttl) throws CircuitAbortException {
		putCachedValue(key, serializable, ttl);
	}

	/**
	 * set a key/value mapping in the underlying cache using the default cache TTL
	 * and return preexisting value if any
	 * 
	 * @param key
	 *            cache key. should be serializable depending on the underlying
	 *            implementation
	 * @param serializable
	 *            value to be set. should be serializable depending on the
	 *            underlying implementation
	 * @return previous value if any
	 * @throws CircuitAbortException
	 *             If a cache error occurs.
	 */
	public Object putCachedValue(Object key, Object serializable) throws CircuitAbortException {
		return putCachedValue(key, serializable, 0);
	}

	/**
	 * set a key/value mapping in the underlying cache using a specific TTL and
	 * return preexisting value if any
	 * 
	 * @param key
	 *            cache key. should be serializable depending on the underlying
	 *            implementation
	 * @param serializable
	 *            value to be set. should be serializable depending on the
	 *            underlying implementation
	 * @param ttl
	 *            overridden TTL for this mapping
	 * @return previous value if any
	 * @throws CircuitAbortException
	 *             If a cache error occurs.
	 */
	public abstract Object putCachedValue(Object key, Object serializable, int ttl) throws CircuitAbortException;

	/**
	 * Retrieve the cached value mapped by the given key
	 * 
	 * @param key
	 *            key to be retrieved
	 * @return mapped value or null if none
	 * @throws CircuitAbortException
	 *             if any error occurs.
	 */
	public abstract Object getCachedValue(Object key) throws CircuitAbortException;

	/**
	 * Removes the cached value mapped by the given key
	 * 
	 * @param key
	 *            key to be retrieved
	 * @return 'true' if a cache entry was effectively removed, 'false' otherwise.
	 * @throws CircuitAbortException
	 *             if any error occurs.
	 */
	public abstract boolean removeCacheEntry(Object key) throws CircuitAbortException;

	/**
	 * @return cache key/value mappings count.
	 * @throws CircuitAbortException
	 *             if any error occurs.
	 */
	public abstract int getCacheSize() throws CircuitAbortException;

	/**
	 * Check if the cache is empty.
	 * 
	 * @return 'true' if the given cache is empty, 'false' otherwise.
	 * @throws CircuitAbortException
	 *             if any error occurs.
	 */
	public boolean isCacheEmpty() throws CircuitAbortException {
		return getCacheSize() == 0;
	}

	/**
	 * remove all mappings caontained in this cache.
	 * 
	 * @throws CircuitAbortException
	 *             if any error occurs.
	 */
	public void clear() throws CircuitAbortException {
		Iterator<?> iterator = keys();

		while (iterator.hasNext()) {
			iterator.next();
			iterator.remove();
		}
	}

	/**
	 * compute underlying implementation TTL from a duration in milli-seconds
	 * 
	 * @param millis
	 *            duration in milli-seconds
	 * @return TTL value compatible with the underlying implementation. -1 means
	 *         entry already expired
	 */
	public int expireTTL(long millis) {
		long ttl = -1L;

		if (millis < 0) {
			ttl = -1L;
		} else {
			ttl = millis;
			ttl /= 1000L;

			if ((ttl == 0L) && (millis > 0)) {
				ttl += 1L;
			}
		}

		return (int) ttl;
	}

	/**
	 * compute underlying implementation TTL from an expiration timestamp and a time
	 * reference. This is a convenience method which use {@link #expireTTL(long)}.
	 * 
	 * @param exp
	 *            expiration timestamp
	 * @param time
	 *            time reference
	 * @return TTL value compatible with the underlying implementation. -1 means
	 *         entry already expired
	 */
	public int expireTTL(long exp, long time) {
		long millis = exp - time;

		return expireTTL(millis);
	}

	/**
	 * retrieve cache keys as Iterator
	 * 
	 * @return all keys present in cache.
	 * @throws CircuitAbortException if unable to retrieve cache key iterator
	 */
	public abstract Iterator<?> keys() throws CircuitAbortException;

	/**
	 * retrieve cache values as iterator
	 * 
	 * @return all values present in cache
	 * @throws CircuitAbortException if unable to retrieve cache value iterator
	 */
	public Iterator<?> values() throws CircuitAbortException {
		/*
		 * return a simple iterator which retrieve value for each key. sub classes are
		 * free to override for optimization
		 */
		return new Iterator<Object>() {
			private final Iterator<?> iterator = keys();

			private boolean remove = false;
			private Object value = null;

			@Override
			public boolean hasNext() {
				if (value == null) {
					remove = false;

					try {
						while ((value == null) && iterator.hasNext()) {
							Object key = iterator.next();

							value = key == null ? null : getCachedValue(key);
						}
					} catch (CircuitAbortException e) {
						Trace.error("unable to retrieve value from key", e);
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
					return value;
				} finally {
					remove = true;
					value = null;
				}
			}

			@Override
			public void remove() {
				if (!remove) {
					throw new IllegalStateException("no previous value returned by 'next()'");
				}

				remove = false;
				iterator.remove();
			}
		};
	}

	protected Iterator<Entry<Object, Object>> entries() throws CircuitAbortException {
		return new Iterator<Entry<Object, Object>>() {
			private final Iterator<?> iterator = keys();

			private boolean remove = false;
			private Entry<Object, Object> entry = null;

			@Override
			public boolean hasNext() {
				if (entry == null) {
					try {
						remove = false;

						while ((entry == null) && iterator.hasNext()) {
							Object key = iterator.next();

							if (key != null) {
								Object value = getCachedValue(key);

								if (value != null) {
									entry = new CacheEntry(CacheResource.this, key, value);
								}
							}
						}
					} catch (CircuitAbortException e) {
						Trace.error("Unable iterate in Cache", e);
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
					return entry;
				} finally {
					remove = true;
					entry = null;
				}
			}

			@Override
			public void remove() {
				if (!remove) {
					throw new IllegalStateException("no previous value returned by 'next()'");
				}

				remove = false;
				iterator.remove();
			}
		};
	}

	/**
	 * @return a Map&lt;Object,Object&gt; representing this cache resource.
	 */
	@Override
	public final CacheMapView getResourceView() {
		return new CacheMapView(this);
	}

	/**
	 * wraps an existing key iterator to add the capability of removing keys by
	 * calling iterator's remove() method.
	 * 
	 * @param <T> key type
	 * @param iterator input key iterator
	 * @return wrapped key iterator with remove() capability
	 */
	protected final <T> Iterator<T> wrapKeyIterator(final Iterator<T> iterator) {
		return new Iterator<T>() {
			private T remove = null;
			private T key = null;

			@Override
			public boolean hasNext() {
				if (key == null) {
					remove = null;

					while ((key == null) && iterator.hasNext()) {
						key = iterator.next();
					}
				}

				return key != null;
			}

			@Override
			public T next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}

				try {
					return key;
				} finally {
					remove = key;
					key = null;
				}
			}

			@Override
			public void remove() {
				if (remove == null) {
					throw new IllegalStateException("no previous key returned by 'next()'");
				}

				try {
					removeCacheEntry(remove);
				} catch (CircuitAbortException e) {
					Trace.error("Unable to remove cache key", e);
				} finally {
					remove = null;
				}
			}
		};
	}

	/**
	 * wraps an existing cache resource overriding the default cache TTL.
	 * 
	 * @param resource input cache resource
	 * @param ttl ttl to be applied
	 * @return wrapped cache resource with the given ttl
	 */
	public static CacheResource wrap(CacheResource resource, int ttl) {
		return new CacheResource() {
			@Override
			public boolean isKeyInCache(Object key) throws CircuitAbortException {
				return resource.isKeyInCache(key);
			}

			@Override
			public boolean isValueInCache(Object value) throws CircuitAbortException {
				return resource.isValueInCache(value);
			}

			@Override
			public void setCachedValue(Object key, Object serializable) throws CircuitAbortException {
				resource.setCachedValue(key, serializable, ttl);
			}

			@Override
			public void setCachedValue(Object key, Object serializable, int ttl) throws CircuitAbortException {
				super.setCachedValue(key, serializable, ttl);
			}

			@Override
			public Object putCachedValue(Object key, Object serializable) throws CircuitAbortException {
				return resource.putCachedValue(key, serializable, ttl);
			}

			@Override
			public Object putCachedValue(Object key, Object serializable, int ttl) throws CircuitAbortException {
				return resource.putCachedValue(key, serializable, ttl);
			}

			@Override
			public Object getCachedValue(Object key) throws CircuitAbortException {
				return resource.getCachedValue(key);
			}

			@Override
			public boolean removeCacheEntry(Object key) throws CircuitAbortException {
				return resource.removeCacheEntry(key);
			}

			@Override
			public int getCacheSize() throws CircuitAbortException {
				return resource.getCacheSize();
			}

			@Override
			public Iterator<?> keys() throws CircuitAbortException {
				return resource.keys();
			}

			@Override
			public boolean isCacheEmpty() throws CircuitAbortException {
				return resource.isCacheEmpty();
			}

			@Override
			public void clear() throws CircuitAbortException {
				resource.clear();
			}

			@Override
			public int expireTTL(long millis) {
				return resource.expireTTL(millis);
			}

			@Override
			public int expireTTL(long exp, long time) {
				return resource.expireTTL(exp, time);
			}

			@Override
			public Iterator<?> values() throws CircuitAbortException {
				return resource.values();
			}

			@Override
			protected Iterator<Entry<Object, Object>> entries() throws CircuitAbortException {
				return resource.entries();
			}
		};
	}

	public static class CacheEntry extends SimpleEntry<Object, Object> {
		/**
		 * 
		 */
		private static final long serialVersionUID = 8614302856852793444L;

		private final CacheResource store;

		public CacheEntry(CacheResource store, Object key, Object value) {
			super(key, value);

			this.store = store;
		}

		@Override
		public Object setValue(Object value) {
			Object previous = null;
			Object key = getKey();

			try {
				previous = store == null ? null : store.putCachedValue(key, value);
			} catch (CircuitAbortException e) {
				Trace.error("Unable to put value in Cache", e);
			}

			super.setValue(value);

			return previous;
		}
	}
}
