package com.vordel.circuit.filter.devkit.oauth2.model;

import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.err_invalid_request;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_prompt;

import java.util.AbstractSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vordel.circuit.filter.devkit.oauth2.jaxrs.OAuthException;

public class PromptSet extends AbstractSet<String> {
	/**
	 * prompt delimiters regular expression
	 */
	private static final Pattern LEXER_REGEX = Pattern.compile("([\\s]*)([^\\s]+)");
	/**
	 * prompt grammar matcher
	 */
	private static final Pattern PARSER_REGEX = Pattern.compile("(none|login|consent|select_account)");

	private final ObjectNode parameters;

	public PromptSet(ObjectNode parameters) {
		this.parameters = parameters;
	}

	public static Iterator<String> iterator(String prompt) {
		return new PromptIterator(prompt);
	}

	public static Set<String> asSet(String prompt) {
		Iterator<String> iterator = PromptSet.iterator(prompt);
		Set<String> types = new LinkedHashSet<String>();

		while (iterator.hasNext()) {
			types.add(iterator.next());
		}

		return types;
	}

	public static String asString(Iterator<String> iterator) {
		String prompt = null;

		if ((iterator != null) && iterator.hasNext()) {
			StringBuilder builder = new StringBuilder().append(assertValidPrompt(iterator.next()));
			Set<String> used = new HashSet<String>();

			while (iterator.hasNext()) {
				String next = iterator.next();
				
				if ((next != null) && (!next.isEmpty()) && used.add(next)) {
					builder.append(' ');
					builder.append(assertValidPrompt(next));
				}
			}

			prompt = builder.toString();
		}

		return prompt;
	}

	@Override
	public Iterator<String> iterator() {
		String prompt = parameters == null ? null : parameters.path(param_prompt).asText(null);

		return new PromptIterator(prompt) {
			@Override
			protected void update(String prompt) {
				if ((prompt == null) || (prompt.isEmpty())) {
					parameters.remove(param_prompt);
				} else {
					parameters.put(param_prompt, prompt);
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
		Iterator<String> iterator = new PromptIterator(e);
		String item = null;
		boolean added = false;

		if (iterator.hasNext()) {
			item = iterator.next();

			if (iterator.hasNext()) {
				item = null;
			}
		}

		if (item == null) {
			throw new IllegalArgumentException(String.format("'%s' is not a valid prompt (OpenID Connect Core section 3.1.2.1)", e));
		}

		if (added = (!contains(item))) {
			StringBuilder builder = new StringBuilder();

			if (parameters == null) {
				throw new UnsupportedOperationException("no backing store for prompts");
			}

			String prompt = parameters == null ? null : parameters.path(param_prompt).asText(null);

			if ((prompt != null) && (!prompt.isEmpty())) {
				builder.append(prompt);
				builder.append(' ');

				if (contains("none") || ("none".equals(item))) {
					throw new IllegalArgumentException("the 'none' prompt is exclusive");
				}
			}
			
			prompt = builder.append(item).toString();

			if ((prompt == null) || (prompt.isEmpty())) {
				parameters.remove(param_prompt);
			} else {
				parameters.put(param_prompt, prompt);
			}
		}

		return added;
	}

	public static String assertValidPrompt(String value) {
		if ((value == null) || (!PARSER_REGEX.matcher(value).matches())) {
			throw new OAuthException(err_invalid_request, null, String.format("'%s' is not a valid prompt (OpenID Connect Core section 3.1.2.1)", value));
		}
		
		return value;
	}

	public static class PromptIterator implements Iterator<String> {
		private final Set<String> used = new HashSet<String>();
		private final Matcher parser;
		private final Matcher lexer;

		private String prompt = null;
		private String cursor = null;

		private int start = -1;
		private int end = -1;

		public PromptIterator(String prompt) {
			if (prompt == null) {
				prompt = "";
			}

			this.lexer = LEXER_REGEX.matcher(prompt);
			this.parser = PARSER_REGEX.matcher(prompt);
			this.prompt = prompt;
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
							throw new OAuthException(err_invalid_request, null, String.format("'%s' is not a valid prompt (OpenID Connect Core section 3.1.2.1)", group));
						} else {
							this.cursor = group;
							this.start = start;
							this.end = end;

							if (!used.add(group)) {
								/* prompt has already been seen */
								group = null;

								try {
									/* remove it */
									remove();
								} catch(UnsupportedOperationException e) {

								}
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
				throw new IllegalStateException("no previous prompt returned by next()");
			}

			try {
				StringBuilder builder = new StringBuilder();

				if (start > 0) {
					builder.append(prompt.substring(0, start - 1));
				}

				builder.append(prompt.substring(end, prompt.length()));

				prompt = builder.toString();
				lexer.reset(prompt);
				parser.reset(prompt);

				update(prompt);
			} finally {
				start = -1;
				end = -1;
			}
		}

		protected void update(String prompt) {
			throw new UnsupportedOperationException();
		}
	}
}
