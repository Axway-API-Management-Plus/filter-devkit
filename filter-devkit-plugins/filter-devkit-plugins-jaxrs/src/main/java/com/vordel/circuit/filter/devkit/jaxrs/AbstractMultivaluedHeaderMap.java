package com.vordel.circuit.filter.devkit.jaxrs;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.MultivaluedMap;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.mime.FormURLEncodedBody;
import com.vordel.mime.HeaderSet;
import com.vordel.mime.Headers;
import com.vordel.mime.QueryStringHeaderSet;

public abstract class AbstractMultivaluedHeaderMap extends AbstractMap<String, List<String>> implements MultivaluedMap<String, String>, Headers {
	private final Set<Entry<String, List<String>>> entrySet = new AbstractHeadersEntries() {
		@Override
		protected Headers getHeaders() {
			return AbstractMultivaluedHeaderMap.this.getHeaders();
		}
	};
	private final Set<String> keySet = new AbstractHeadersNameSet() {
		@Override
		protected Headers getHeaders() {
			return AbstractMultivaluedHeaderMap.this.getHeaders();
		}
	};
	private final Collection<List<String>> values = new AbstractHeadersValues() {
		@Override
		protected Headers getHeaders() {
			return AbstractMultivaluedHeaderMap.this.getHeaders();
		}
	};

	protected abstract Headers getHeaders();

	public HeaderSet toHeaderSet() {
		return MultivaluedHeaderMap.fromHeaders(new HeaderSet(), (Headers) this);
	}

	public QueryStringHeaderSet toQueryStringHeaderSet() {
		return MultivaluedHeaderMap.fromHeaders(new QueryStringHeaderSet(), (Headers) this);
	}

	public FormURLEncodedBody toFormURLEncodedBody() throws CircuitAbortException {
		FormURLEncodedBody body = FormURLEncodedBody.createBody();

		try {
			MultivaluedHeaderMap.fromHeaders(body.getParameters(), (Headers) this);
		} catch (IOException e) {
			throw new CircuitAbortException("Unable to encode FormURLEncodedBody", e);
		}

		return body;
	}

	/*
	 * start by vordel specific headers method wrappers
	 */

	@Override
	public void setHeader(String key, String value) {
		Headers store = getHeaders();

		if (store == null) {
			throw new UnsupportedOperationException();
		}

		store.setHeader(key, value);
	}

	@Override
	public void addHeader(String key, String value) {
		Headers store = getHeaders();

		if (store == null) {
			throw new UnsupportedOperationException();
		}

		store.addHeader(key, value);
	}

	@Override
	public Iterator<String> getHeaders(String key) {
		Iterator<String> iterator = null;
		Headers store = getHeaders();

		if (!containsKey(key)) {
			List<String> empty = Collections.emptyList();

			iterator = empty.iterator();
		} else {
			iterator = store.getHeaders(key);
		}

		return iterator;
	}

	@Override
	public int getHeadersSize(String key) {
		Headers store = getHeaders();

		if (store == null) {
			return 0;
		}

		return store.getHeadersSize(key);
	}

	@Override
	public Iterator<String> getHeaderNames() {
		Iterator<String> iterator = null;
		Headers store = getHeaders();

		if (store == null) {
			List<String> empty = Collections.emptyList();

			iterator = empty.iterator();
		} else {
			iterator = store.getHeaderNames();
		}

		return iterator;
	}

	/*
	 * the following methods are standard jax-rs/collection stuff
	 */

	@Override
	public void putSingle(String key, String value) {
		Headers store = getHeaders();

		if (store == null) {
			throw new UnsupportedOperationException();
		}

		store.setHeader(key, value);
	}

	@Override
	public void add(String key, String value) {
		Headers store = getHeaders();

		if (store == null) {
			throw new UnsupportedOperationException();
		}

		store.addHeader(key, value);
	}

	@Override
	public String getFirst(String key) {
		Headers store = getHeaders();
		Iterator<String> iterator = store.getHeaders(key);

		if ((iterator != null) && iterator.hasNext()) {
			return iterator.next();
		}

		return null;
	}

	@Override
	public void addAll(String key, String... newValues) {
		if (newValues != null) {
			for (String value : newValues) {
				add(key, value);
			}
		}
	}

	@Override
	public void addAll(String key, List<String> valueList) {
		if (valueList != null) {
			for (String value : valueList) {
				add(key, value);
			}
		}
	}

	@Override
	public int size() {
		return keySet().size();
	}

	@Override
	public boolean isEmpty() {
		return keySet().isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return keySet().contains(key);
	}

	@Override
	public Set<String> keySet() {
		return keySet;
	}

	@Override
	public boolean containsValue(Object value) {
		return values().contains(value);
	}

	@Override
	public Collection<List<String>> values() {
		return values;
	}

	@Override
	public List<String> put(String key, List<String> value) {
		Headers store = getHeaders();

		if (store instanceof AbstractMultivaluedHeaderMap) {
			/* try to delegate storage using underlying store */
			return ((AbstractMultivaluedHeaderMap) store).put(key, value);
		} else {
			/* other implementations can't keep provided reference */
			throw new UnsupportedOperationException();
		}
	}

	@Override
	public List<String> get(Object key) {
		return AbstractHeadersValues.asValueList(getHeaders(), key);
	}

	@Override
	public List<String> remove(Object key) {
		Headers store = getHeaders();
		List<String> removed = null;

		if (store instanceof AbstractMultivaluedHeaderMap) {
			removed = ((AbstractMultivaluedHeaderMap) store).remove(key);
		} else if (key instanceof String) {
			if (store instanceof HeaderSet) {
				removed = AbstractHeadersValues.asValueList(((HeaderSet) store).getHeaderEntry((String) key));

				((HeaderSet) store).remove((String) key);
			} else if (store instanceof QueryStringHeaderSet) {
				removed = AbstractHeadersValues.asValueList(((QueryStringHeaderSet) store).remove((String) key));
			} else {
				// XXX code this (should not occur)
				throw new UnsupportedOperationException();
			}

		}

		return removed;
	}

	@Override
	public void addFirst(String key, String value) {
		Headers headers = getHeaders();

		if (headers instanceof AbstractMultivaluedHeaderMap) {
			((AbstractMultivaluedHeaderMap) headers).addFirst(key, value);
		} else if (headers instanceof HeaderSet) {
			HeaderSet.HeaderEntry header = ((HeaderSet) headers).getHeaderEntry(key);

			if (header == null) {
				((HeaderSet) headers).setHeader(key, value);
			} else {
				header.add(0, new HeaderSet.Header(value));
			}
		} else if (headers instanceof QueryStringHeaderSet) {
			QueryStringHeaderSet.QueryStringHeader header = ((QueryStringHeaderSet) headers).getHeader(key);

			if (header == null) {
				((QueryStringHeaderSet) headers).setHeader(key, value);
			} else {
				header.getStringValuesArray().add(0, value);
			}
		} else if (getFirst(key) == null) {
			putSingle(key, value);
		} else {
			throw new UnsupportedOperationException();
		}
	}

	@Override
	public boolean equalsIgnoreValueOrder(MultivaluedMap<String, String> otherMap) {
		/*
		 * This code is a cut'n'paste of reference implementation of
		 * AbstractMultivaluedMap.
		 */
		if (this == otherMap) {
			return true;
		}
		if (!keySet().equals(otherMap.keySet())) {
			return false;
		}
		for (Entry<String, List<String>> e : entrySet()) {
			List<String> olist = otherMap.get(e.getKey());
			if (e.getValue().size() != olist.size()) {
				return false;
			}
			for (String v : e.getValue()) {
				if (!olist.contains(v)) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public Set<Entry<String, List<String>>> entrySet() {
		return entrySet;
	}
}
