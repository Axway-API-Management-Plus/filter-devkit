package com.vordel.circuit.filter.devkit.oauth2.model;

import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.err_rfc6749_invalid_scope;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_scope;

import java.util.AbstractSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vordel.circuit.filter.devkit.oauth2.jaxrs.OAuthException;

public class ScopeSet extends AbstractSet<String> {
	/**
	 * scopes delimiters regular expression, takes the coma as a separator character
	 */
	private static final Pattern SCOPES_REGEX = Pattern.compile("([\\s,]*)([^\\s,]+)");
	/**
	 * scopes grammar matcher
	 */
	private static final Pattern NQCHAR_REGEX = Pattern.compile("([\\u0021\\u0023-\\u002b\\u002d-\\u005b\\u005d-\\u007e]+)");

	private final ObjectNode parameters;
	
	public ScopeSet(ObjectNode parameters) {
		this.parameters = parameters;
	}
	
	public static Iterator<String> iterator(String scope) {
		return new ScopeIterator(scope, false);
	}

	public static String asString(Iterator<String> iterator) {
		String scope = null;

		if ((iterator != null) && iterator.hasNext()) {
			StringBuilder builder = new StringBuilder().append(assertValidScope(iterator.next()));
			Set<String> used = new HashSet<String>();

			while (iterator.hasNext()) {
				String next = iterator.next();
				
				if ((next != null) && (!next.isEmpty()) && used.add(next)) {
					builder.append(' ');
					builder.append(assertValidScope(next));
				}
			}

			scope = builder.toString();
		}

		return scope;
	}

	public static <T> String asString(Iterator<T> iterator, Function<T, String> converter) {
		String scope = null;

		if ((iterator != null) && iterator.hasNext()) {
			StringBuilder builder = new StringBuilder().append(assertValidScope(converter.apply(iterator.next())));
			Set<String> used = new HashSet<String>();

			while (iterator.hasNext()) {
				String next = converter.apply(iterator.next());
				
				if ((next != null) && (!next.isEmpty()) && used.add(next)) {
					builder.append(' ');
					builder.append(assertValidScope(next));
				}
			}

			scope = builder.toString();
		}

		return scope;
	}
	
	@Override
	public Iterator<String> iterator() {
		String scope = parameters == null ? null : parameters.path(param_scope).asText(null);

		return new ScopeIterator(scope, true) {
			@Override
			protected void update(String scope) {
				if ((scope == null) || (scope.isEmpty())) {
					parameters.remove(param_scope);
				} else {
					parameters.put(param_scope, scope);
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

	@Override
	public boolean add(String e) {
		Iterator<String> iterator = new ScopeIterator(e, false);
		String item = null;
		boolean added = false;

		if (iterator.hasNext()) {
			item = iterator.next();

			if (iterator.hasNext()) {
				item = null;
			}
		}

		if (item == null) {
			throw new IllegalArgumentException(String.format("'%s' is not a valid scope (RFC6749 appendix A.4)", e));
		}

		if (added = (!contains(item))) {
			StringBuilder builder = new StringBuilder();
			
			if (parameters == null) {
				throw new UnsupportedOperationException("no backing store for scopes");
			}
			
			String scope = parameters == null ? null : parameters.path(param_scope).asText(null);

			if ((scope != null) && (!scope.isEmpty())) {
				builder.append(scope);
				builder.append(' ');
			}
			
			scope = builder.append(item).toString();

			if ((scope == null) || (scope.isEmpty())) {
				parameters.remove(param_scope);
			} else {
				parameters.put(param_scope, scope);
			}
		}

		return added;
	}
	
	public static final String assertValidScope(String scope) {
		if ((scope == null) || (!NQCHAR_REGEX.matcher(scope).matches())) {
			throw new OAuthException(err_rfc6749_invalid_scope, null, String.format("'%s' is not a valid scope (RFC6749 appendix A.4)", scope));
		}
		
		return scope;
	}

	public static class ScopeIterator implements Iterator<String> {
		private final Set<String> used = new HashSet<String>();
		private final Matcher parser;
		private final Matcher lexer;
		private final boolean update;

		private String scope = null;
		private String cursor = null;

		private int start = -1;
		private int end = -1;

		public ScopeIterator(String scope, boolean update) {
			if (scope == null) {
				scope = "";
			}

			this.lexer = SCOPES_REGEX.matcher(scope);
			this.parser = NQCHAR_REGEX.matcher(scope);
			this.scope = scope;
			this.update = update;
		}

		@Override
		public boolean hasNext() {
			if (cursor == null) {
				start = -1;
				end = -1;

				while ((cursor == null) && lexer.find()) {
					String group = lexer.group(2);

					if (!group.isEmpty()) {
						int start = lexer.start(2);
						int end = lexer.end(2);
						
						if (!parser.region(start, end).find(start) || (!parser.group(1).equals(group))) {
							throw new OAuthException(err_rfc6749_invalid_scope, null, String.format("'%s' is not a valid scope (RFC6749 appendix A.4)", group));
						} else {
							this.start = start;
							this.end = end;

							if (!used.add(group)) {
								/* scope has already been seen */
								group = null;

								if (update) {
									/* remove it */
									remove();
								}
							} else {
								this.cursor = group;
							}
						}
					}
				}
			}

			return cursor != null;
		}

		@Override
		public String next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			try {
				return cursor;
			} finally {
				cursor = null;
			}
		}

		@Override
		public void remove() {
			if ((start == -1) || (end == -1) || (cursor != null)) {
				throw new IllegalStateException("no previous scope returned by next()");
			}

			try {
				StringBuilder builder = new StringBuilder();

				if (start > 0) {
					builder.append(scope.substring(0, start - 1));
				}

				builder.append(scope.substring(end, scope.length()));

				scope = builder.toString();
				lexer.reset(scope);
				parser.reset(scope);

				update(scope);
			} finally {
				start = -1;
				end = -1;
			}
		}

		protected void update(String scope) {
			throw new UnsupportedOperationException();
		}
	}
}
