package com.vordel.sdk.plugins.oauth2.model;

import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_invalid_request;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_response_type;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_scope;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.response_type_code;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.response_type_id_token;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.response_type_none;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.response_type_token;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vordel.sdk.plugins.oauth2.jaxrs.OAuthException;

public class ResponseTypeSet extends AbstractSet<String> {
	/**
	 * response_type parameter regular expression
	 */
	private static final Pattern RESPONSETYPE_REGEX = Pattern.compile("([\\s]*)([^\\s]+)");
	private static final Pattern RESPONSENAME_REGEX = Pattern.compile("([_0-9a-zA-Z]+)");
	private static final List<String> INDEXES = Arrays.asList(response_type_none, response_type_code, response_type_id_token, response_type_token);

	private static final Comparator<String> COMPARATOR = new Comparator<String>() {
		@Override
		public int compare(String o1, String o2) {
			int i1 = INDEXES.indexOf(o1);
			int i2 = INDEXES.indexOf(o2);

			return Integer.compare(i1, i2);
		}
	};

	private final ObjectNode parameters;

	public ResponseTypeSet(ObjectNode parameters) {
		this.parameters = parameters;
	}

	public static Iterator<String> iterator(String response_type) {
		return new ResponseTypeIterator(response_type);
	}

	public static List<String> asList(String response_type) {
		Iterator<String> iterator = ResponseTypeSet.iterator(response_type);
		List<String> types = new ArrayList<String>();

		while (iterator.hasNext()) {
			types.add(iterator.next());
		}

		/* remove any invalid response type */
		if (types.retainAll(INDEXES)) {
			throw new OAuthException(err_invalid_request, null, "the response_type parameter contains unsupported values");
		}
		
		if (types.contains(response_type_none) && types.size() > 1) {
			/* if 'none' response_type is requested it must be the only response_type requested */
			throw new OAuthException(err_invalid_request, null, "the 'none' response_type is exclusive");
		}

		/* sort response types */
		Collections.sort(types, ResponseTypeSet.COMPARATOR);

		return types;
	}

	public static String asString(Iterator<String> iterator) {
		String response_type = null;

		if ((iterator != null) && iterator.hasNext()) {
			StringBuilder builder = new StringBuilder().append(iterator.next());
			Set<String> used = new HashSet<String>();

			while (iterator.hasNext()) {
				String next = iterator.next();
				
				if ((next != null) && (!next.isEmpty()) && used.add(next)) {
					builder.append(' ');
					builder.append(next);
				}
			}

			response_type = builder.toString();
		}

		return response_type;
	}

	@Override
	public Iterator<String> iterator() {
		String response_type = parameters == null ? null : parameters.path(param_response_type).asText(null);

		return new ResponseTypeIterator(response_type) {
			@Override
			protected void update(String response_type) {
				if ((response_type == null) || (response_type.isEmpty())) {
					parameters.remove(param_response_type);
				} else {
					parameters.put(param_response_type, response_type);
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
		Iterator<String> iterator = new ResponseTypeIterator(e);
		String item = null;
		boolean added = false;

		if (iterator.hasNext()) {
			item = iterator.next();

			if (iterator.hasNext()) {
				item = null;
			}
		}

		if (item == null) {
			throw new IllegalArgumentException(String.format("'%s' is not a valid response_type (RFC6749 appendix A.3)", e));
		}

		if (added = (!contains(item))) {
			StringBuilder builder = new StringBuilder();

			if (parameters == null) {
				throw new UnsupportedOperationException("no backing store for response_types");
			}

			String response_type = parameters == null ? null : parameters.path(param_response_type).asText(null);

			if ((response_type != null) && (!response_type.isEmpty())) {
				builder.append(response_type);
				builder.append(' ');
				
				if (contains(response_type_none) || (response_type_none.equals(item))) {
					throw new IllegalArgumentException("the 'none' response_type is exclusive");
				}
			}
			
			response_type = builder.append(item).toString();

			if ((response_type == null) || (response_type.isEmpty())) {
				parameters.remove(param_scope);
			} else {
				parameters.put(param_scope, response_type);
			}
		}

		return added;
	}

	public static class ResponseTypeIterator implements Iterator<String> {
		private final Set<String> used = new HashSet<String>();
		private final Matcher lexer;
		private final Matcher parser;

		private String response_type = null;
		private String cursor = null;

		private int start = -1;
		private int end = -1;

		public ResponseTypeIterator(String response_type) {
			if (response_type == null) {
				response_type = "";
			}

			this.lexer = RESPONSETYPE_REGEX.matcher(response_type);
			this.parser = RESPONSENAME_REGEX.matcher(response_type);
			this.response_type = response_type;
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
							throw new OAuthException(err_invalid_request, null, String.format("'%s' is not a valid response_type (RFC6749 appendix A.3)", group));
						} else {
							this.cursor = group;
							this.start = start;
							this.end = end;

							if (!used.add(group)) {
								/* response_type has already been seen */
								group = null;

								/* remove it */
								remove();
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
				throw new IllegalStateException("no previous response_type returned by next()");
			}

			try {
				StringBuilder builder = new StringBuilder();

				if (start > 0) {
					builder.append(response_type.substring(0, start - 1));
				}

				builder.append(response_type.substring(end, response_type.length()));

				response_type = builder.toString();
				lexer.reset(response_type);
				parser.reset(response_type);

				update(response_type);
			} finally {
				start = -1;
				end = -1;
			}
		}

		protected void update(String response_type) {
			throw new UnsupportedOperationException();
		}
	}
}
