package com.vordel.circuit.script.context.resources;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.cache.BodySerializer;
import com.vordel.circuit.cache.CacheContainer;
import com.vordel.config.ConfigContext;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;
import com.vordel.es.xes.PortableESPK;
import com.vordel.mime.Body;
import com.vordel.trace.Trace;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;

public class EHCacheResource extends CacheResource {
	private final String cacheName;

	public EHCacheResource(ConfigContext ctx, Entity entity, String reference) throws EntityStoreException {
		this(ctx, entity.getReferenceValue(reference));
	}

	public EHCacheResource(ConfigContext ctx, ESPK cacheToUse) throws EntityStoreException {
		this(getCacheName(ctx, cacheToUse));
	}

	public EHCacheResource(String cacheName) {
		this.cacheName = cacheName;
	}

	private static String getCacheName(ConfigContext ctx, ESPK cacheToUse) {
		EntityStore es = ctx.getStore();
		PortableESPK ppk = PortableESPK.toPortableKey(es, cacheToUse);

		return ppk.getFieldValueOfReferencedEntity("name");
	}

	public static Cache getCache(String cacheName) throws CircuitAbortException {
		Cache cache = CacheContainer.getInstance().getCache(cacheName);

		if (cache == null) {
			Trace.error(String.format("Unable to find the cache '%s'", cacheName));

			throw new CircuitAbortException(String.format("Invalid cache name configured: %s", cacheName));
		}

		return cache;
	}

	@Override
	public boolean isKeyInCache(Object key) throws CircuitAbortException {
		Cache cache = getCache(cacheName);

		return (cache != null) && cache.isKeyInCache(key);
	}

	@Override
	public boolean isValueInCache(Object value) throws CircuitAbortException {
		try {
			Cache cache = getCache(cacheName);

			return (cache != null) && cache.isValueInCache(asSerializable(value));
		} catch (IOException e) {
			throw new CircuitAbortException("Unexpected I/O Exception", e);
		}
	}

	@Override
	public void setCachedValue(Object key, Object value, int ttl) throws CircuitAbortException {
		if ((value == null) || (ttl < 0)) {
			removeCacheEntry(key);
		} else {
			Cache cache = getCache(cacheName);

			if (cache != null) {
				boolean exists = cache.isKeyInCache(key);
				Element element = null;

				if ((!exists) && !isCacheWriteable()) {
					if (Trace.isDebugEnabled()) {
						String msg = String.format("Not adding key '%s'  to cache, disk persistence is on and the maximum elements has been reached", String.valueOf(key));

						Trace.debug(msg);
					}
				} else {
					Serializable serializable = null;

					try {
						serializable = asSerializable(value);
					} catch (IOException e) {
						throw new CircuitAbortException("Unable to serialize value", e);
					}

					if (serializable instanceof Serializable) {
						element = new Element(key, serializable);

						if (ttl > 0) {
							element.setTimeToLive(ttl);
						}

						if (exists) {
							cache.replace(element);
						} else {
							cache.put(element);
						}
					} else {
						String className = value != null ? value.getClass().getSimpleName() : "null";
						String msg = String.format("Trying to cache object that does not implement java.io.Serializable: %s", className);

						throw new CircuitAbortException(msg);
					}
				}
			}
		}
	}

	@Override
	public Object putCachedValue(Object key, Object value, int ttl) throws CircuitAbortException {
		try {
			Cache cache = getCache(cacheName);
			Element existing = null;

			if (cache != null) {
				if ((value == null) || (ttl < 0)) {
					existing = cache.get(key);

					cache.remove(key);
				} else {
					Serializable serializable = asSerializable(value);

					if (serializable instanceof Serializable) {
						Element element = new Element(key, serializable);

						if (ttl > 0) {
							element.setTimeToLive(ttl);
						}

						existing = cache.getQuiet(key);

						if (existing == null) {
							if (!isCacheWriteable()) {
								if (Trace.isDebugEnabled()) {
									String msg = String.format("Not adding key '%s'  to cache, disk persistence is on and the maximum elements has been reached", String.valueOf(key));

									Trace.debug(msg);
								}
							}

							cache.put(element);
						} else {
							cache.replace(element);
						}
					} else {
						String className = value != null ? value.getClass().getSimpleName() : "null";
						String msg = String.format("Trying to cache object that does not implement java.io.Serializable: %s", className);

						throw new CircuitAbortException(msg);
					}
				}
			}

			return asObject(existing);
		} catch (IOException e) {
			throw new CircuitAbortException("Unexpected I/O Exception", e);
		}
	}

	@Override
	public Object getCachedValue(Object key) throws CircuitAbortException {
		Cache cache = getCache(cacheName);
		Object value = null;

		if (cache != null) {
			Element element = cache.get(key);

			if (element != null) {
				Trace.debug("Key is in the cache already. Element serializable: " + element.isSerializable());
			}

			try {
				value = asObject(element);
			} catch (IOException e) {
				throw new CircuitAbortException("Unexpected I/O Exception", e);
			}
		}

		return value;
	}

	@Override
	public boolean removeCacheEntry(Object key) throws CircuitAbortException {
		Cache cache = getCache(cacheName);
		boolean result = false;

		if (cache != null) {
			try {
				result = cache.remove(key);

				if (result && Trace.isDebugEnabled()) {
					Trace.debug("Removed item with key [" + String.valueOf(key) + "] from cache [" + cacheName + "]");
				}
			} catch (RuntimeException e) {
				Trace.error("Error removing from cache", e);
			}
		}

		return result;
	}

	@Override
	public int getCacheSize() throws CircuitAbortException {
		Cache cache = getCache(cacheName);

		/*
		 * use the fast variant
		 */
		return cache == null ? 0 : getCacheSize(cache, false);
	}

	@Override
	public void clear() throws CircuitAbortException {
		Cache cache = getCache(cacheName);

		if (cache != null) {
			cache.removeAll();
		}
	}

	@Override
	public Iterator<?> keys() throws CircuitAbortException {
		Cache cache = getCache(cacheName);
		Iterator<?> keys = null;

		if (cache == null) {
			Set<?> empty = Collections.emptySet();

			keys = empty.iterator();
		} else {
			keys = cache.getKeys().iterator();
		}

		return wrapKeyIterator(keys);
	}

	private boolean isCacheWriteable() throws CircuitAbortException {
		Cache cache = getCache(cacheName);

		return (cache != null) && isCacheWriteable(cache);
	}

	@SuppressWarnings("deprecation")
	private static boolean isCacheWriteable(Cache cache) {
		boolean writeable = false;

		/*
		 * XXX deprecated methods are used here... should be replaced
		 */
		if (cache != null) {
			CacheConfiguration configuration = cache.getCacheConfiguration();

			if (configuration != null) {
				writeable = !configuration.isDiskPersistent();

				if (!writeable) {
					writeable = getCacheSize(cache, true) <= configuration.getMaxElementsOnDisk();
				}
			}
		}

		return writeable;
	}

	private static int getCacheSize(Cache cache, boolean duplicateCheck) {
		int size = 0;

		if (cache != null) {
			List<?> keys = duplicateCheck ? cache.getKeys() : cache.getKeysNoDuplicateCheck();

			size = keys == null ? 0 : keys.size();
		}

		return size;
	}

	private static Serializable asSerializable(Object value) throws IOException {
		if (value instanceof Body) {
			value = new BodySerializer((Body) value);
		}

		return value instanceof Serializable ? (Serializable) value : null;
	}

	private static Object asObject(Element element) throws IOException {
		Object result = null;

		if (element != null) {
			result = element.getObjectValue();

			if (result instanceof BodySerializer) {
				result = ((BodySerializer) result).getAsBody();
			}
		}

		return result;
	}
}
