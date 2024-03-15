package com.vordel.circuit.filter.devkit.context.resources;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.trace.Trace;

public class CacheMapView extends AbstractMap<Object, Object> {
	private final Set<Entry<Object, Object>> entries;
	private final Collection<Object> values;
	private final Set<Object> keys;

	private final CacheResource resource;

	public CacheMapView(CacheResource resource) {
		this.resource = resource;

		this.keys = new KeySet(resource);
		this.entries = new EntrySet(resource);
		this.values = new ValueCollection(resource);
	}

	@Override
	public int size() {
		return size(resource);
	}

	@Override
	public boolean isEmpty() {
		return isEmpty(resource);
	}

	@Override
	public void clear() {
		clear(resource);
	}

	@Override
	public Collection<Object> values() {
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
	public Object get(Object key) {
		Object value = null;

		if (resource != null) {
			try {
				value = resource.getCachedValue(key);
			} catch (CircuitAbortException e) {
				Trace.error("Unable to retrieve value from Cache", e);
			}
		}

		return value;
	}

	@Override
	public Object put(Object key, Object value) {
		Object previous = null;

		if (value == null) {
			previous = remove(key);
		} else if (resource != null) {
			try {
				previous = resource.putCachedValue(key, value);
			} catch (CircuitAbortException e) {
				Trace.error("Unable to put value in Cache", e);
			}
		}

		return previous;
	}

	@Override
	public Object remove(Object key) {
		Object previous = null;

		if (resource != null) {
			try {
				previous = resource.putCachedValue(key, null);
			} catch (CircuitAbortException e) {
				Trace.error("Unable to put value in Cache", e);
			}
		}

		return previous;
	}

	@Override
	public Set<Entry<Object, Object>> entrySet() {
		return entries;
	}

	private static boolean isEmpty(CacheResource resource) {
		boolean empty = true;

		if (resource != null) {
			try {
				empty = resource.isCacheEmpty();
			} catch (CircuitAbortException e) {
				Trace.error("Unable to get Cache size", e);
			}
		}

		return empty;
	}

	private static void clear(CacheResource resource) {
		if (resource != null) {
			try {
				resource.clear();
			} catch (CircuitAbortException e) {
				Trace.error("Unable to clear Cache", e);
			}
		}
	}

	private static int size(CacheResource resource) {
		int size = 0;

		if (resource != null) {
			try {
				size = resource.getCacheSize();
			} catch (CircuitAbortException e) {
				Trace.error("Unable to get Cache size", e);
			}
		}

		return size;
	}

	public static class KeySet extends AbstractSet<Object> {
		private final CacheResource resource;

		private KeySet(CacheResource store) {
			this.resource = store;
		}

		@Override
		public Iterator<Object> iterator() {
			Iterator<Object> iterator = null;

			if (resource != null) {
				try {
					/* make generics happy ! */
					iterator = new Iterator<Object>() {
						private final Iterator<?> iterator = resource.keys();

						@Override
						public boolean hasNext() {
							return iterator.hasNext();
						}

						@Override
						public Object next() {
							return iterator.next();
						}

						@Override
						public void remove() {
							iterator.remove();
						}
					};
				} catch (CircuitAbortException e) {
					Trace.error("Unable to get Cache keys", e);
				}
			}

			if (iterator == null) {
				Set<Object> empty = Collections.emptySet();

				iterator = empty.iterator();
			}

			return iterator;
		}

		@Override
		public boolean isEmpty() {
			return CacheMapView.isEmpty(resource);
		}

		@Override
		public boolean contains(Object o) {
			boolean contained = false;

			try {
				contained = resource == null ? false : resource.isKeyInCache(o);
			} catch (CircuitAbortException e) {
				Trace.error("Unable to check key in Cache", e);
			}

			return contained;
		}

		@Override
		public boolean remove(Object o) {
			boolean removed = false;

			try {
				removed = resource == null ? false : resource.removeCacheEntry(o);
			} catch (CircuitAbortException e) {
				Trace.error("Unable to remove key in Cache", e);
			}

			return removed;
		}

		@Override
		public void clear() {
			CacheMapView.clear(resource);
		}

		@Override
		public int size() {
			return CacheMapView.size(resource);
		}
	}

	public static class ValueCollection extends AbstractCollection<Object> {
		private final CacheResource resource;

		public ValueCollection(CacheResource resource) {
			this.resource = resource;
		}

		@Override
		public Iterator<Object> iterator() {
			Iterator<Object> iterator = null;

			if (resource != null) {
				try {
					/* make generics happy ! */
					iterator = new Iterator<Object>() {
						private final Iterator<?> iterator = resource.values();

						@Override
						public boolean hasNext() {
							return iterator.hasNext();
						}

						@Override
						public Object next() {
							return iterator.next();
						}

						@Override
						public void remove() {
							iterator.remove();
						}
					};
				} catch (CircuitAbortException e) {
					Trace.error("Unable to get Cache values", e);
				}
			}

			if (iterator == null) {
				Set<Object> empty = Collections.emptySet();

				iterator = empty.iterator();
			}

			return iterator;
		}

		@Override
		public boolean contains(Object o) {
			boolean contained = false;

			try {
				contained = resource == null ? false : resource.isValueInCache(o);
			} catch (CircuitAbortException e) {
				Trace.error("Unable to check key in Cache", e);
			}

			return contained;
		}

		@Override
		public void clear() {
			CacheMapView.clear(resource);
		}

		@Override
		public boolean isEmpty() {
			return CacheMapView.isEmpty(resource);
		}

		@Override
		public int size() {
			return CacheMapView.size(resource);
		}
	}

	public static class EntrySet extends AbstractSet<Entry<Object, Object>> {
		private final CacheResource resource;

		public EntrySet(CacheResource resource) {
			this.resource = resource;
		}

		@Override
		public Iterator<Entry<Object, Object>> iterator() {
			Iterator<Entry<Object, Object>> iterator = null;

			if (resource != null) {
				try {
					iterator = resource.entries();
				} catch (CircuitAbortException e) {
					Trace.error("Unable to get Cache entries", e);
				}
			}

			if (iterator == null) {
				Set<Entry<Object, Object>> empty = Collections.emptySet();

				iterator = empty.iterator();
			}

			return iterator;
		}

		@Override
		public boolean isEmpty() {
			return CacheMapView.isEmpty(resource);
		}

		@Override
		public void clear() {
			CacheMapView.clear(resource);
		}

		@Override
		public int size() {
			return CacheMapView.size(resource);
		}
	}
}
