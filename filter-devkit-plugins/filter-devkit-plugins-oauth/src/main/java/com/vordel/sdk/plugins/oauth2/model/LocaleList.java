package com.vordel.sdk.plugins.oauth2.model;

import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_invalid_request;

import java.util.AbstractSequentialList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IllformedLocaleException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vordel.sdk.plugins.oauth2.jaxrs.OAuthException;

public class LocaleList extends AbstractSequentialList<String> {
	/**
	 * locale delimiters regular expression
	 */
	private static final Pattern LEXER_REGEX = Pattern.compile("([\\s]*)([^\\s]+)");

	private final ObjectNode parameters;
	private final String claim;

	public LocaleList(ObjectNode parameters, String claim) {
		this.parameters = parameters;
		this.claim = claim;
	}

	public static ListIterator<String> listIterator(String locales, int index) {
		return new LocaleIterator(locales, index);
	}

	public static String normalizeLanguageTag(String value) {
		try {
			return value == null ? null : Locale.forLanguageTag(value).toLanguageTag();
		} catch (IllformedLocaleException e) {
			throw new OAuthException(err_invalid_request, null, String.format("'%s' is not a valid language tag (RFC 5646)", value));
		}
	}

	private static StringBuilder append(Set<String> used, StringBuilder builder, String locale) {
		if ((locale != null) && (!locale.isEmpty()) && used.add(locale)) {
			if (builder.length() > 0) {
				builder.append(' ');
			}

			builder.append(locale);
		}

		return builder;
	}

	public static Iterable<String> asList(String ui_locales) {
		Iterator<String> iterator = ResponseTypeSet.iterator(ui_locales);
		List<String> locales = new ArrayList<String>();

		while (iterator.hasNext()) {
			locales.add(iterator.next());
		}

		return locales;
	}

	public static String asString(Iterator<String> iterator) {
		String locales = null;

		if ((iterator != null) && iterator.hasNext()) {
			Set<String> used = new HashSet<String>();
			StringBuilder builder = append(used, new StringBuilder(), iterator.next());

			while (iterator.hasNext()) {
				append(used, new StringBuilder(), iterator.next());
			}

			locales = builder.toString();
		}

		return locales;
	}

	@Override
	public ListIterator<String> listIterator(int index) {
		String locales = parameters == null ? null : parameters.path(claim).asText(null);

		return new LocaleIterator(locales, index) {
			@Override
			protected void update(String locales) {
				if ((locales == null) || (locales.isEmpty())) {
					parameters.remove(claim);
				} else {
					parameters.put(claim, locales);
				}
			}

		};
	}

	@Override
	public int size() {
		Iterator<String> iterator = iterator();
		int size = 0;

		while ((size < Integer.MAX_VALUE) && iterator.hasNext()) {
			iterator.next();
			size++;
		}

		return size;
	}
	
	public static final String assertValidLocale(String locale) {
		if (normalizeLanguageTag(locale) == null) {
			throw new OAuthException(err_invalid_request, null, String.format("'%s' is not a valid language tag (RFC 5646)", locale));
		}
		
		return locale;
	}

	public static class LocaleIterator implements ListIterator<String> {
		private final List<String> matches;

		private int cursor;
		private int last;

		public LocaleIterator(String locales, int index) {
			this.matches = new ArrayList<String>();

			if (locales == null) {
				locales = "";
			}

			Matcher lexer = LEXER_REGEX.matcher(locales);
			ListIterator<String> iterator = matches.listIterator();
			Set<String> used = new HashSet<String>(); /* don't repeat yourself */

			while(lexer.find()) {
				String locale = lexer.group(2);

				if ((locale != null) && (!locale.isEmpty()) && used.add(locale)) {
					iterator.add(locale);
				}
			}

			this.cursor = matches.listIterator(index).nextIndex();
			this.last = cursor;
		}

		@Override
		public boolean hasNext() {
			return cursor < matches.size();
		}

		@Override
		public String next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			try {
				return normalizeLanguageTag(matches.get(cursor));
			} finally {
				last = cursor;
				cursor += 1;
			}
		}

		@Override
		public boolean hasPrevious() {
			return cursor > 0;
		}

		@Override
		public String previous() {
			if (!hasPrevious()) {
				throw new NoSuchElementException();
			}

			try {
				return normalizeLanguageTag(matches.get(cursor));
			} finally {
				last = cursor;
				cursor -= 1;
			}
		}

		@Override
		public int nextIndex() {
			return cursor;
		}

		@Override
		public int previousIndex() {
			return cursor - 1;
		}

		@Override
		public void remove() {
			if (Math.abs(last - cursor) != 1) {
				throw new IllegalStateException();
			}

			try {
				matches.remove(last);

				update(asString(matches.iterator()));
			} finally {
				cursor = last;
			}
		}

		@Override
		public void set(String e) {
			if ((e = normalizeLanguageTag(e)) == null) {
				throw new IllegalStateException("language tag can't be null");
			}

			if (Math.abs(last - cursor) != 1) {
				throw new IllegalStateException();
			}

			try {
				matches.set(last, e);

				update(asString(matches.iterator()));
			} finally {
				cursor = last;
			}
		}

		@Override
		public void add(String e) {
			if ((e = normalizeLanguageTag(e)) == null) {
				throw new IllegalStateException("language tag can't be null");
			}

			if (Math.abs(last - cursor) != 1) {
				throw new IllegalStateException();
			}

			try {
				matches.add(last, e);

				update(asString(matches.iterator()));
			} finally {
				cursor = last;
			}
		}

		protected void update(String locales) {
			throw new UnsupportedOperationException();
		}
	}
}
