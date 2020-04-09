package com.vordel.sdk.plugins.oauth2.model;

import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_invalid_request;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import javax.ws.rs.core.MultivaluedMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vordel.mime.Headers;
import com.vordel.sdk.plugins.oauth2.jaxrs.OAuthException;

public abstract class OAuthParameter<T> {
	public boolean contained(ObjectNode root, String name) {
		return (root != null) && root.has(name);
	}

	public abstract T get(ObjectNode root, String name);

	public abstract T put(ObjectNode root, String name, String value);

	public abstract T put(ObjectNode root, String name, Iterable<String> values);

	public abstract <H extends Headers> H toQueryString(ObjectNode root, String name, H query);

	public final JsonNode parse(ObjectNode root, String name, ObjectNode body, MultivaluedMap<String, String> merged, BiFunction<String, String, String> validator) {
		return parse(root, name, body == null ? null : body.remove(name), merged == null ? null : merged.remove(name), validator);
	}

	public JsonNode parse(ObjectNode root, String name, JsonNode node, Iterable<String> values, BiFunction<String, String, String> validator) {
		return parse(root, name, node == null ? null : node.asText(null), values, validator);
	}

	public final JsonNode parse(ObjectNode root, String name, String value, Iterable<String> values, BiFunction<String, String, String> validator) {
		Set<String> candidates = new LinkedHashSet<String>();

		root.remove(name);
		add(candidates, name, value, validator);

		if (values != null) {
			for (String candidate : values) {
				add(candidates, name, candidate, validator);
			}
		}

		Iterator<String> iterator = candidates.iterator();

		if (iterator.hasNext()) {
			String single = iterator.next();

			if (iterator.hasNext()) {
				put(root, name, candidates);
			} else {
				put(root, name, single);
			}
		}

		return root.path(name);
	}

	private static void add(Collection<String> values, String name, String value, BiFunction<String, String, String> validator) {
		if ((value != null) && value.isEmpty()) {
			value = null;
		}

		if ((validator != null) && (!values.contains(value))) {
			value = validator.apply(name, value);
		}

		if (value != null) {
			values.add(value);
		}
	}

	private static final Pattern VSCHAR_REGEX = Pattern.compile("([\\u0020-\\u007e]+)");

	public static abstract class SingleString extends OAuthParameter<String> {
		@Override
		public String put(ObjectNode root, String name, Iterable<String> values) {
			throw new OAuthException(err_invalid_request, null, String.format("duplicate parameter '%s'", name));
		}

		@Override
		public <T extends Headers> T toQueryString(ObjectNode root, String name, T query) {
			if (query != null) {
				String value = get(root, name);

				if (value != null) {
					query.addHeader(name, value);
				}
			}

			return query;
		}
	}

	public static final OAuthParameter<String> GENERIC_SINGLE = new SingleString() {
		@Override
		public String get(ObjectNode root, String name) {
			return root.path(name).asText(null);
		}

		@Override
		public String put(ObjectNode root, String name, String value) {
			JsonNode result = root.remove(name);

			if (value != null) {
				root.put(name, value);
			}

			return result == null ? null : result.asText(null);
		}
	};

	public static final OAuthParameter<String> CLIENT_ID = new SingleString() {
		@Override
		public String get(ObjectNode root, String name) {
			return root.path(name).asText(null);
		}

		@Override
		public String put(ObjectNode root, String name, String value) {
			JsonNode result = root.remove(name);

			if (value != null) {
				if (!VSCHAR_REGEX.matcher(value).matches()) {
					throw new OAuthException(err_invalid_request, null, String.format("'%s' is not a valid client_id (RFC6749 appendix A.1)", value));
				}

				root.put(name, value);
			}

			return result == null ? null : result.asText(null);
		}
	};

	public static final OAuthParameter<String> SCOPES = new OAuthParameter<String>() {
		@Override
		public String get(ObjectNode root, String name) {
			return root.path(name).asText(null);
		}

		@Override
		public String put(ObjectNode root, String name, String value) {
			Set<String> parsed = append(new LinkedHashSet<String>(), value);

			return put(root, name, parsed);
		}

		@Override
		public String put(ObjectNode root, String name, Iterable<String> values) {
			Set<String> parsed = new LinkedHashSet<String>();

			for (String scopes : values) {
				append(parsed, scopes);
			}

			return put(root, name, parsed);
		}

		private String put(ObjectNode root, String name, Set<String> parsed) {
			StringBuilder builder = new StringBuilder();
			JsonNode result = root.remove(name);

			for (String scope : parsed) {
				if (builder.length() > 0) {
					builder.append(' ');
				}

				builder.append(scope);
			}

			String value = builder.toString();

			if (!value.isEmpty()) {
				root.put(name, value);
			}

			return result == null ? null : result.asText(null);
		}

		@Override
		public JsonNode parse(ObjectNode root, String name, JsonNode node, Iterable<String> values, BiFunction<String, String, String> validator) {
			if (node instanceof ArrayNode) {
				/*
				 * special case, handle scopes given as json array. In this case, handle each
				 * value as a single scope.
				 */
				List<String> scopes = new ArrayList<String>();

				for (JsonNode value : (ArrayNode) node) {
					String scope = value.asText(null);

					if (scope != null) {
						scopes.add(ScopeSet.assertValidScope(scope));
					}
				}

				if (values != null) {
					/* append existing values from query if any */
					for (String value : values) {
						if (value != null) {
							scopes.add(value);
						}
					}
				}

				values = scopes;
				node = null;
			}

			return super.parse(root, name, node, values, validator);
		}

		private Set<String> append(Set<String> parsed, String scopes) {
			if (scopes != null) {
				Iterator<String> iterator = ScopeSet.iterator(scopes);

				while (iterator.hasNext()) {
					parsed.add(iterator.next());
				}
			}

			return parsed;
		}

		@Override
		public <T extends Headers> T toQueryString(ObjectNode root, String name, T query) {
			if (query != null) {
				String value = get(root, name);

				if (value != null) {
					query.addHeader(name, value);
				}
			}

			return query;
		}
	};
}
