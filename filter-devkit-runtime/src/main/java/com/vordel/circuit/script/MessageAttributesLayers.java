package com.vordel.circuit.script;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

public class MessageAttributesLayers extends AbstractMap<String, Object> {
	private final Set<Entry<String, Object>> entries = new AbstractSet<Entry<String, Object>>() {
		@Override
		public Iterator<Entry<String, Object>> iterator() {
			return new UnionEntryIterator(peek());
		}

		@Override
		public int size() {
			Iterator<Entry<String, Object>> iterator = iterator();
			int size = 0;

			while ((size < Integer.MAX_VALUE) && iterator.hasNext()) {
				iterator.next();
				size++;
			}

			return size;
		}
	};

	private final Deque<Layer> layers = new LinkedList<Layer>();

	@Override
	public boolean containsKey(Object key) {
		Layer layer = peek();

		return layer == null ? false : layer.containsKey(key);
	}

	@Override
	public Object get(Object key) {
		Layer layer = peek();

		return layer == null ? false : layer.get(key);
	}

	@Override
	public Object put(String key, Object value) {
		Layer layer = peek();

		if (layer == null) {
			throw new UnsupportedOperationException();
		}

		return layer.put(key, value);
	}

	@Override
	public Object remove(Object key) {
		Layer layer = peek();

		if (layer == null) {
			throw new UnsupportedOperationException();
		}

		return layer.remove(key);
	}

	public Layer push(Map<String, Object> store, Set<Object> inputs) {
		if (store == null) {
			throw new IllegalArgumentException("store cannot be null");
		}

		Set<Object> whiteouts = new HashSet<Object>();
		Layer parent = peek();

		return push(new Layer(store, inputs, whiteouts, parent));
	}

	public Layer push(Map<String, Object> store, Set<Object> inputs, Set<Object> whiteouts) {
		if (store == null) {
			throw new IllegalArgumentException("store cannot be null");
		}

		if (whiteouts == null) {
			throw new IllegalArgumentException("whiteouts cannot be null");
		}

		return push(new Layer(store, inputs, whiteouts, peek()));
	}

	public Layer push(Layer layer) {
		if (layer != null) {
			layers.push(layer);
		}

		return layer;
	}

	public Layer peek() {
		return layers.peek();
	}

	public Layer pop() {
		return layers.pop();
	}

	/**
	 * @return the first pushed layer (globals). pushing back the first layer will
	 *         hide all other existing layers until it is popped.
	 */
	public Layer first() {
		return layers.peekLast();
	}

	@Override
	public Set<Entry<String, Object>> entrySet() {
		return entries;
	}

	public static class Layer extends AbstractMap<String, Object> {
		/**
		 * set of removed attributes for this layer (present in parent and absent at
		 * this level).
		 */
		private final Set<Object> whiteouts;
		/**
		 * set of input keys. any modification of the layer will be removed from this
		 * map.
		 */
		private final Set<Object> inputs;
		/**
		 * key/value mapping for this layer
		 */
		private final Map<String, Object> store;
		/**
		 * inherited layer (optional)
		 */
		private final Layer parent;
		/**
		 * Enable get(), containsKey() and remove() in the underlying store. In the case
		 * of global attributes, those operations MUST be delegated to the regular
		 * APIGateway runtime.
		 */
		private final boolean storeLookup;

		public Layer(Map<String, Object> store, Set<Object> inputs, Set<Object> whiteouts, Layer parent) {
			this(store, inputs, whiteouts, parent, true);
		}

		public Layer(Map<String, Object> store, Set<Object> inputs, Set<Object> whiteouts, Layer parent, boolean storeLookup) {
			this.store = store;
			this.inputs = inputs;
			this.parent = parent;
			this.storeLookup = storeLookup && (store != null);
			this.whiteouts = whiteouts;
		}

		private Object get(Object key, boolean forceStoreLookup) {
			Object value = null;

			if (whiteouts.contains(key)) {
				/* value is hidden by a whiteout */
			} else if (forceStoreLookup | storeLookup) {
				if ((store != null) && store.containsKey(key)) {
					/* apply get to local layer if the key is in store or whiteouts */
					value = store.get(key);
				} else if (parent != null) {
					/* if this layer does not know the key, apply get to parent */
					value = parent.get(key, forceStoreLookup);
				}
			}

			return value;
		}

		private boolean containsKey(Object key, boolean forceStoreLookup) {
			boolean contained = false;

			if (forceStoreLookup | storeLookup) {
				if (!whiteouts.contains(key)) {
					contained |= (store != null) && store.containsKey(key);

					if ((!contained) && (parent != null)) {
						/* if the key is not known, delegates to parent */
						contained = parent.containsKey(key, forceStoreLookup);
					}
				}
			}

			return contained;
		}

		@Override
		public Object get(Object key) {
			return get(key, true);
		}

		@Override
		public Object put(String key, Object value) {
			Object previous = null;

			if (store == null) {
				throw new UnsupportedOperationException();
			}

			if (store.containsKey(key) || whiteouts.contains(key)) {
				/*
				 * if the key is available locally, the previous value come from local storage
				 */
				previous = store.put(key, value);
			} else if (parent != null) {
				/*
				 * if the key is not available, get previous value from parent and apply put
				 * locally
				 */
				previous = parent.get(key);
				store.put(key, value);
			} else {
				/*
				 * last case, no key in hierarchy, juste store mapping in backing store.
				 */
				previous = store.put(key, value);
			}

			whiteouts.remove(key);
			inputs.remove(key);

			return previous;
		}

		@Override
		public boolean containsKey(Object key) {
			return containsKey(key, true);
		}

		@Override
		public Object remove(Object key) {
			Object removed = null;

			if (store == null) {
				whiteouts.add(key);
			} else if (whiteouts.contains(key)) {
				/* nothing to do here... */
			} else {
				if (store.containsKey(key)) {
					removed = store.remove(key);
				} else if (parent != null) {
					removed = parent.get(key, true);
				}

				/*
				 * If the parent layer contains this key, add a whiteout to hide parent
				 * attribute.
				 */
				if ((parent != null) && (parent.containsKey(key, true))) {
					whiteouts.add(key);
				}
			}

			inputs.remove(key);

			return removed;
		}

		@Override
		public Set<String> keySet() {
			Set<String> entries = null;

			if (store == null) {
				Map<String, Object> empty = Collections.emptyMap();

				entries = empty.keySet();
			} else {
				entries = store.keySet();
			}

			return entries;
		}

		@Override
		public Set<Entry<String, Object>> entrySet() {
			Set<Entry<String, Object>> entries = null;

			if (store == null) {
				Map<String, Object> empty = Collections.emptyMap();

				entries = empty.entrySet();
			} else {
				entries = store.entrySet();
			}

			return entries;
		}
	}

	private static class UnionEntryIterator implements Iterator<Entry<String, Object>> {
		private final Set<Object> seen = new HashSet<Object>();
		private final Layer top;

		private Iterator<Entry<String, Object>> iterator;
		private Layer layer;

		private Entry<String, Object> next = null;
		private Entry<String, Object> last = null;

		private UnionEntryIterator(Layer layer) {
			this.top = layer;
			this.layer = layer;
			this.iterator = iterator(layer);

			whiteouts(seen, layer);
		}

		private static Iterator<Entry<String, Object>> iterator(Layer layer) {
			Map<String, Object> store = layer == null ? null : layer.store;

			if (store == null) {
				store = Collections.emptyMap();
			}

			return store.entrySet().iterator();
		}

		private static void whiteouts(Set<Object> whiteouts, Layer layer) {
			if ((layer != null) && (layer.whiteouts != null)) {
				whiteouts.addAll(layer.whiteouts);
			}
		}

		@Override
		public boolean hasNext() {
			if (next == null) {
				/* start by disabling calls to 'remove' */
				last = null;

				while ((!hasNextEntry()) && (layer != null)) {
					layer = layer.parent;
					iterator = iterator(layer);

					whiteouts(seen, layer);
				}
			}

			return next != null;
		}

		private boolean hasNextEntry() {
			while ((next == null) && (iterator.hasNext())) {
				Entry<String, Object> cursor = iterator.next();

				if (seen.add(cursor.getKey())) {
					next = cursor;
				}
			}

			return next != null;
		}

		@Override
		public Entry<String, Object> next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			try {
				return next;
			} finally {
				last = next;
				next = null;
			}
		}

		@Override
		public void remove() {
			if (last == null) {
				throw new IllegalStateException("No previous call to next");
			}

			String key = last.getKey();

			if (layer == top) {
				/* we are at the top layer, use iterator.remove() and update whiteouts */
				iterator.remove();

				if ((layer.parent != null) && (layer.parent.containsKey(key))) {
					layer.whiteouts.add(key);
				}
			} else {
				/* we are in an underlying layer, apply operation on top */
				top.remove(key);
			}
		}
	}
}
