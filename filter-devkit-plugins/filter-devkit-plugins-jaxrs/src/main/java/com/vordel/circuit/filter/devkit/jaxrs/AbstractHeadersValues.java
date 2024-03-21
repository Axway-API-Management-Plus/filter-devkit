package com.vordel.circuit.filter.devkit.jaxrs;

import java.util.AbstractCollection;
import java.util.AbstractSequentialList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import com.vordel.mime.HeaderSet;
import com.vordel.mime.Headers;
import com.vordel.mime.QueryStringHeaderSet;

public abstract class AbstractHeadersValues extends AbstractCollection<List<String>> {
	protected abstract Headers getHeaders();

	protected static List<String> asValueList(List<HeaderSet.Header> entry) {
		return entry == null ? null : new HeaderEntryList(entry);
	}

	protected static List<String> asValueList(QueryStringHeaderSet.QueryStringHeader entry) {
		return entry == null ? null : entry.getStringValuesArray();
	}

	protected static List<String> asValueList(Headers store, Object key) {
		List<String> values = null;

		if (store instanceof AbstractMultivaluedHeaderMap) {
			values = ((AbstractMultivaluedHeaderMap) store).get(key);
		} else if (key instanceof String) {
			if (store instanceof HeaderSet) {
				values = AbstractHeadersValues.asValueList(((HeaderSet) store).getHeaderEntry((String) key));
			} else if (store instanceof QueryStringHeaderSet) {
				values = AbstractHeadersValues.asValueList(((QueryStringHeaderSet) store).getHeader((String) key));
			} else {
				// XXX code this (should not occur)
				throw new UnsupportedOperationException();
			}
		}

		return values;
	}

	@Override
	public Iterator<List<String>> iterator() {
		return new ValueListIterator(getHeaders());
	}

	@Override
	public int size() {
		return AbstractHeadersNameSet.size(getHeaders());
	}

	public static class ValueListIterator implements Iterator<List<String>> {
		private final Iterator<String> iterator;
		private final Headers headers;

		private List<String> values = null;
		private String remove = null;
		private String name = null;

		public ValueListIterator(Headers headers) {
			this.iterator = AbstractHeadersNameSet.iterator(headers);
			this.headers = headers;
		}

		@Override
		public boolean hasNext() {
			if (name == null) {
				remove = null;

				while ((name == null) && iterator.hasNext()) {
					name = iterator.next();
					values = asValueList(headers, name);
				}
			}

			return name != null;
		}

		@Override
		public List<String> next() {
			try {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}

				return values;
			} finally {
				remove = name;
				name = null;
				values = null;
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

	public static class HeaderEntryList extends AbstractSequentialList<String> {
		private final List<HeaderSet.Header> list;

		public HeaderEntryList(List<HeaderSet.Header> list) {
			this.list = list;
		}

		@Override
		public ListIterator<String> listIterator(int index) {
			return new HeaderEntryListIterator(list, index);
		}

		@Override
		public int size() {
			return list == null ? 0 : list.size();
		}

		@Override
		public boolean isEmpty() {
			return (list == null) || list.isEmpty();
		}

		@Override
		public boolean add(String e) {
			if (list == null) {
				throw new UnsupportedOperationException();
			}

			return list.add(new HeaderSet.Header(e));
		}

		@Override
		public void clear() {
			if (list == null) {
				throw new UnsupportedOperationException();
			}

			list.clear();
		}

		@Override
		public String get(int index) {
			HeaderSet.Header value = list == null ? null : list.get(index);

			return value == null ? null : value.toString();
		}

		@Override
		public String set(int index, String element) {
			if (list == null) {
				throw new UnsupportedOperationException();
			}

			HeaderSet.Header value = list.set(index, new HeaderSet.Header(element));

			return value == null ? null : value.toString();
		}

		@Override
		public void add(int index, String element) {
			if (list == null) {
				throw new UnsupportedOperationException();
			}

			list.add(index, new HeaderSet.Header(element));
		}

		@Override
		public String remove(int index) {
			if (list == null) {
				throw new UnsupportedOperationException();
			}

			HeaderSet.Header value = list.remove(index);

			return value == null ? null : value.toString();
		}
	}

	public static class HeaderEntryListIterator implements ListIterator<String> {
		private final ListIterator<HeaderSet.Header> iterator;

		public HeaderEntryListIterator(List<HeaderSet.Header> list, int index) {
			if (list == null) {
				list = Collections.emptyList();
			}

			this.iterator = list.listIterator(index);
		}

		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override
		public String next() {
			HeaderSet.Header next = iterator.next();

			return next == null ? null : next.toString();
		}

		@Override
		public boolean hasPrevious() {
			return iterator.hasPrevious();
		}

		@Override
		public String previous() {
			HeaderSet.Header previous = iterator.previous();

			return previous == null ? null : previous.toString();
		}

		@Override
		public int nextIndex() {
			return iterator.nextIndex();
		}

		@Override
		public int previousIndex() {
			return iterator.previousIndex();
		}

		@Override
		public void remove() {
			iterator.remove();
		}

		@Override
		public void set(String e) {
			iterator.set(new HeaderSet.Header(e));
		}

		@Override
		public void add(String e) {
			iterator.add(new HeaderSet.Header(e));
		}
	}
}
