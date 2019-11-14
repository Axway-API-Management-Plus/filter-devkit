package com.vordel.circuit.jaxrs;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.vordel.mime.Headers;

public abstract class AbstractHeadersEntries extends AbstractSet<Map.Entry<String, List<String>>> {
	protected abstract Headers getHeaders();

	@Override
	public Iterator<Map.Entry<String, List<String>>> iterator() {
		return new EntryIterator(getHeaders());
	}

	@Override
	public int size() {
		return AbstractHeadersNameSet.size(getHeaders());
	}

	public static class EntryIterator implements Iterator<Map.Entry<String, List<String>>> {
		private final Iterator<String> iterator;
		private final Headers headers;

		private Map.Entry<String, List<String>> entry = null;
		private String remove = null;

		public EntryIterator(Headers headers) {
			this.iterator = AbstractHeadersNameSet.iterator(headers);
			this.headers = headers;
		}

		@Override
		public boolean hasNext() {
			if (entry == null) {
				remove = null;

				while ((entry == null) && iterator.hasNext()) {
					String name = iterator.next();
					List<String> values = AbstractHeadersValues.asValueList(headers, name);

					entry = new AbstractMap.SimpleImmutableEntry<String, List<String>>(name, values);
				}
			}

			return entry != null;
		}

		@Override
		public Map.Entry<String, List<String>> next() {
			try {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}

				return entry;
			} finally {
				remove = entry.getKey();
				entry = null;
			}
		}

		@Override
		public void remove() {
			if (remove == null) {
				throw new IllegalStateException();
			}

			iterator.remove();
			remove = null;
		}
	}

}
