package com.vordel.circuit.jaxrs;

import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import javax.ws.rs.core.MultivaluedMap;

import com.vordel.mime.HeaderSet;
import com.vordel.mime.Headers;
import com.vordel.mime.QueryStringHeaderSet;

public abstract class AbstractHeadersNameSet extends AbstractSet<String> {
	protected abstract Headers getHeaders();

	protected final static Iterator<String> iterator(Headers store) {
		Iterator<String> iterator = null;

		if (store == null) {
			Set<String> empty = Collections.emptySet();

			iterator = empty.iterator();
		} else {
			iterator = store.getHeaderNames();
		}

		return iterator;
	}

	protected final static int size(Headers store) {
		int size = 0;

		if (store instanceof MultivaluedMap) {
			size = ((MultivaluedMap<?, ?>) store).size();
		} else if (store instanceof HeaderSet) {
			size = ((HeaderSet) store).entrySet().size();
		} else if (store instanceof QueryStringHeaderSet) {
			size = ((QueryStringHeaderSet) store).size();
		} else {
			/*
			 * Unable to use optimized size, count items
			 */
			Iterator<String> iterator = iterator(store);

			while ((size < Integer.MAX_VALUE) && iterator.hasNext()) {
				iterator.next();
			}
		}

		return size;
	}

	@Override
	public Iterator<String> iterator() {
		return iterator(getHeaders());
	}

	@Override
	public int size() {
		return size(getHeaders());
	}

	@Override
	public boolean contains(Object o) {
		Headers store = getHeaders();

		if (store instanceof MultivaluedMap) {
			return ((MultivaluedMap<?, ?>) store).keySet().contains(o);
		} else if (o instanceof String) {
			if (store instanceof HeaderSet) {
				return ((HeaderSet) store).hasHeader((String) o);
			} else if (store instanceof QueryStringHeaderSet) {
				return ((QueryStringHeaderSet) store).containsKey((String) o);
			}
		}

		return super.contains(o);
	}

	@Override
	public boolean remove(Object o) {
		Headers store = getHeaders();

		if (store instanceof MultivaluedMap) {
			return ((MultivaluedMap<?, ?>) store).keySet().remove(o);
		} else if (o instanceof String) {
			if (store instanceof HeaderSet) {
				boolean removed = ((HeaderSet) store).hasHeader((String) o);

				((HeaderSet) store).remove((String) o);

				return removed;
			} else if (store instanceof QueryStringHeaderSet) {
				return ((QueryStringHeaderSet) store).remove((String) o) != null;
			}
		}

		return super.remove(o);
	}
}
