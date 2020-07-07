package com.vordel.sdk.plugins.oauth2.model;

import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_invalid_request;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import javax.ws.rs.core.MultivaluedMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jwt.SignedJWT;
import com.vordel.mime.Headers;
import com.vordel.sdk.plugins.oauth2.jaxrs.OAuthException;

public abstract class OAuthParameter<T> {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	public boolean contained(ObjectNode root, String name) {
		return (root != null) && root.has(name);
	}

	public abstract T get(ObjectNode root, String name);

	public abstract T put(ObjectNode root, String name, T value);

	public abstract T put(ObjectNode root, String name, Iterable<T> values);

	public abstract <H extends Headers> H toQueryString(ObjectNode root, String name, H query);

	public JsonNode parse(ObjectNode root, String name, ObjectNode body, MultivaluedMap<String, String> merged, BiFunction<String, String, String> validator) {
		return parse(root, name, body == null ? null : body.remove(name), merged == null ? null : merged.remove(name), validator);
	}

	public JsonNode parse(ObjectNode root, String name, JsonNode node, Iterable<String> values, BiFunction<String, String, String> validator) {
		return parse(root, name, node == null ? null : node.asText(null), values, validator);
	}

	public abstract JsonNode parse(ObjectNode root, String name, String value, Iterable<String> values, BiFunction<String, String, String> validator);

	public abstract Class<T> getJsonType();

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
	private static final Pattern DISPLAY_REGEX = Pattern.compile("(page|popup|touch|wap)?");
	private static final Pattern RESPONSE_MODE_REGEX = Pattern.compile("(query|fragment|form_post)?");
	
	public static final  Map<String,String> PKCE_METHODS = pkceMethods();
	
	private static final Map<String,String> pkceMethods() {
		Map<String, String> registry = new HashMap<String, String>();
		
		registry.put("plain", null);
		registry.put("S256", "SHA-256");
		registry.put("S384", "SHA-384");
		registry.put("S512", "SHA-512");
		
		return Collections.unmodifiableMap(registry);
	}

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

		@Override
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

		@Override
		public Class<String> getJsonType() {
			return String.class;
		}
	}

	public static abstract class SingleInteger extends OAuthParameter<Integer> {
		@Override
		public Integer put(ObjectNode root, String name, Iterable<Integer> values) {
			throw new OAuthException(err_invalid_request, null, String.format("duplicate parameter '%s'", name));
		}

		@Override
		public <T extends Headers> T toQueryString(ObjectNode root, String name, T query) {
			if (query != null) {
				Integer value = get(root, name);

				if (value != null) {
					query.addHeader(name, value.toString());
				}
			}

			return query;
		}

		@Override
		public final JsonNode parse(ObjectNode root, String name, String value, Iterable<String> values, BiFunction<String, String, String> validator) {
			Set<String> candidates = new LinkedHashSet<String>();

			root.remove(name);
			add(candidates, name, value, validator);

			if (values != null) {
				for (String candidate : values) {
					add(candidates, name, candidate, validator);
				}
			}

			Set<Integer> integers = new LinkedHashSet<Integer>();

			try {
				for(String candidate : candidates) {
					Integer parsed = candidate == null ? null : Integer.parseInt(candidate);

					if (parsed != null) {
						integers.add(parsed);
					}
				}
			} catch (NumberFormatException e) {
				throw new OAuthException(err_invalid_request, null, String.format("can't parse integer for parameter '%s'", name), e);
			}

			Iterator<Integer> iterator = integers.iterator();

			if (iterator.hasNext()) {
				Integer single = iterator.next();

				if (iterator.hasNext()) {
					put(root, name, integers);
				} else {
					put(root, name, single);
				}
			}

			return root.path(name);
		}

		@Override
		public Class<Integer> getJsonType() {
			return Integer.class;
		}
	}

	public static final OAuthParameter<String> STRING_SINGLE = new SingleString() {
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

	public static final OAuthParameter<String> JWT_SINGLE = new SingleString() {
		@Override
		public String get(ObjectNode root, String name) {
			return root.path(name).asText(null);
		}

		@Override
		public String put(ObjectNode root, String name, String value) {
			JsonNode result = root.remove(name);

			if (value != null) {
				try {
					SignedJWT.parse(value).getJWTClaimsSet();

					root.put(name, value);
				} catch (ParseException e) {
					throw new OAuthException(err_invalid_request, null, String.format("'%s' is not a valid Json Web Token", value), e);
				}
			}

			return result == null ? null : result.asText(null);
		}
	};

	public static final OAuthParameter<ObjectNode> JSON_SINGLE = new OAuthParameter<ObjectNode>() {
		@Override
		public ObjectNode get(ObjectNode root, String name) {
			JsonNode node = root.path(name);

			return node instanceof ObjectNode ? (ObjectNode) node : null;
		}

		@Override
		public ObjectNode put(ObjectNode root, String name, ObjectNode value) {
			JsonNode result = root.remove(name);

			if (value != null) {
				root.set(name, value);
			}

			return (result != null) && (result instanceof ObjectNode) ? (ObjectNode) result : null;
		}
		
		@Override
		public ObjectNode put(ObjectNode root, String name, Iterable<ObjectNode> values) {
			throw new OAuthException(err_invalid_request, null, String.format("duplicate parameter '%s'", name));
		}

		@Override
		public <T extends Headers> T toQueryString(ObjectNode root, String name, T query) {
			if (query != null) {
				ObjectNode value = get(root, name);

				if (value != null) {
					query.addHeader(name, value.toString());
				}
			}

			return query;
		}

		@Override
		public JsonNode parse(ObjectNode root, String name, String value, Iterable<String> values, BiFunction<String, String, String> validator) {
			Set<String> candidates = new LinkedHashSet<String>();

			root.remove(name);
			add(candidates, name, value, validator);

			if (values != null) {
				for (String candidate : values) {
					add(candidates, name, candidate, validator);
				}
			}

			Set<ObjectNode> objects = new LinkedHashSet<ObjectNode>();

			try {
				for(String candidate : candidates) {
					if (candidate != null) {
						JsonNode node = MAPPER.readTree(candidate);
						
						if (node instanceof ObjectNode) {
							objects.add((ObjectNode) node);
						}
					}
				}
			} catch (IOException e) {
				throw new OAuthException(err_invalid_request, null, String.format("can't parse json for parameter '%s'", name), e);
			}

			Iterator<ObjectNode> iterator = objects.iterator();

			if (iterator.hasNext()) {
				ObjectNode single = iterator.next();

				if (iterator.hasNext()) {
					put(root, name, objects);
				} else {
					put(root, name, single);
				}
			}

			return root.path(name);
		}

		@Override
		public Class<ObjectNode> getJsonType() {
			return ObjectNode.class;
		}
	};

	public static final OAuthParameter<Integer> INTEGER_SINGLE = new SingleInteger() {
		@Override
		public Integer get(ObjectNode root, String name) {
			JsonNode node = root.path(name);

			return node.isNumber() ? node.asInt() : null;
		}

		@Override
		public Integer put(ObjectNode root, String name, Integer value) {
			JsonNode result = root.remove(name);

			if (value != null) {
				root.put(name, value);
			}

			return (result != null) && result.isNumber() ? result.asInt() : null;
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

	public static final OAuthParameter<String> CLIENT_SECRET = new SingleString() {
		@Override
		public String get(ObjectNode root, String name) {
			return root.path(name).asText(null);
		}

		@Override
		public String put(ObjectNode root, String name, String value) {
			JsonNode result = root.remove(name);

			if (value != null) {
				if (!VSCHAR_REGEX.matcher(value).matches()) {
					throw new OAuthException(err_invalid_request, null, "provided client_secret is not valie (RFC6749 appendix A.1)");
				}

				root.put(name, value);
			}

			return result == null ? null : result.asText(null);
		}
	};

	public static final OAuthParameter<String> STATE = new SingleString() {
		@Override
		public String get(ObjectNode root, String name) {
			return root.path(name).asText(null);
		}

		@Override
		public String put(ObjectNode root, String name, String value) {
			JsonNode result = root.remove(name);

			if (value != null) {
				if (!VSCHAR_REGEX.matcher(value).matches()) {
					throw new OAuthException(err_invalid_request, null, String.format("'%s' is not a valid state (RFC6749 appendix A.5)", value));
				}

				root.put(name, value);
			}

			return result == null ? null : result.asText(null);
		}
	};

	public static final OAuthParameter<String> DISPLAY = new SingleString() {
		@Override
		public String get(ObjectNode root, String name) {
			return root.path(name).asText(null);
		}

		@Override
		public String put(ObjectNode root, String name, String value) {
			JsonNode result = root.remove(name);

			if (value != null) {
				if (!DISPLAY_REGEX.matcher(value).matches()) {
					throw new OAuthException(err_invalid_request, null, String.format("'%s' is not a valid display (OpenID Connect Core section 3.1.2.1)", value));
				}

				root.put(name, value);
			}

			return result == null ? null : result.asText(null);
		}
	};

	public static final OAuthParameter<String> CODE_CHALLENGE_METHOD = new SingleString() {
		@Override
		public String get(ObjectNode root, String name) {
			return root.path(name).asText(null);
		}

		@Override
		public String put(ObjectNode root, String name, String value) {
			JsonNode result = root.remove(name);

			if (value != null) {
				if (!PKCE_METHODS.containsKey(value)) {
					throw new OAuthException(err_invalid_request, null, String.format("'%s' is not a supported code challenge method (see https://www.iana.org/assignments/oauth-parameters/oauth-parameters.xhtml#pkce-code-challenge-method)", value));
				}

				root.put(name, value);
			}

			return result == null ? null : result.asText(null);
		}
	};

	public static final OAuthParameter<String> RESPONSE_MODE = new SingleString() {
		@Override
		public String get(ObjectNode root, String name) {
			return root.path(name).asText(null);
		}

		@Override
		public String put(ObjectNode root, String name, String value) {
			JsonNode result = root.remove(name);

			if (value != null) {
				if (!RESPONSE_MODE_REGEX.matcher(value).matches()) {
					throw new OAuthException(err_invalid_request, null, String.format("'%s' is not a valid response mode (OAuth 2.0 Form Post Response Mode section 2)", value));
				}

				root.put(name, value);
			}

			return result == null ? null : result.asText(null);
		}
	};

	private static abstract class OAuthParameterStringSet extends OAuthParameter<String> {
		@Override
		public String get(ObjectNode root, String name) {
			return root.path(name).asText(null);
		}

		@Override
		public String put(ObjectNode root, String name, String value) {
			return put(root, name, Collections.singleton(value));
		}

		@Override
		public String put(ObjectNode root, String name, Iterable<String> values) {
			JsonNode result = root.remove(name);
			String value = asStringValue(values);

			if ((value != null) && (!value.isEmpty())) {
				root.put(name, value);
			}

			return result == null ? null : result.asText(null);
		}

		@Override
		public JsonNode parse(ObjectNode root, String name, JsonNode node, Iterable<String> values, BiFunction<String, String, String> validator) {
			if (node instanceof ArrayNode) {
				/*
				 * special case, handle values given as json array. In this case, handle each
				 * value as a single one.
				 */
				List<String> array = new ArrayList<String>();

				for (JsonNode value : (ArrayNode) node) {
					String scope = value.asText(null);

					if (scope != null) {
						array.add(ScopeSet.assertValidScope(scope));
					}
				}

				if (values != null) {
					/* append existing values from query if any */
					for (String value : values) {
						if (value != null) {
							array.add(value);
						}
					}
				}

				values = array;
				node = null;
			}

			return super.parse(root, name, node, values, validator);
		}

		@Override
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

		protected abstract String assertValidValue(String value);

		protected abstract String asStringValue(Iterable<String> value);

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

		@Override
		public Class<String> getJsonType() {
			return String.class;
		}
	}

	public static final OAuthParameter<String> SCOPES = new OAuthParameterStringSet() {
		@Override
		protected String assertValidValue(String value) {
			return ScopeSet.assertValidScope(value);
		}

		@Override
		protected String asStringValue(Iterable<String> value) {
			return ScopeSet.asString(value == null ? null : value.iterator());
		}
	};

	public static final OAuthParameter<String> RESPONSE_TYPE = new OAuthParameterStringSet() {
		@Override
		protected String assertValidValue(String value) {
			return ResponseTypeSet.assertValidResponseType(value);
		}

		@Override
		protected String asStringValue(Iterable<String> value) {
			if (value != null) {
				/* sort response types if needed */
				String response_type = ResponseTypeSet.asString(value.iterator());

				value = ResponseTypeSet.asList(response_type);
			}

			return ResponseTypeSet.asString(value == null ? null : value.iterator());
		}
	};

	public static final OAuthParameter<String> PROMPT = new OAuthParameterStringSet() {
		@Override
		protected String assertValidValue(String value) {
			return PromptSet.assertValidPrompt(value);
		}

		@Override
		protected String asStringValue(Iterable<String> value) {
			if (value != null) {
				String prompt = PromptSet.asString(value.iterator());

				value = PromptSet.asSet(prompt);
			}

			return PromptSet.asString(value == null ? null : value.iterator());
		}
	};

	public static final OAuthParameter<String> UI_LOCALES = new OAuthParameterStringSet() {
		@Override
		protected String assertValidValue(String value) {
			return LocaleList.assertValidLocale(value);
		}

		@Override
		protected String asStringValue(Iterable<String> value) {
			if (value != null) {
				String ui_locales = LocaleList.asString(value.iterator());

				value = LocaleList.asList(ui_locales);
			}

			return LocaleList.asString(value == null ? null : value.iterator());
		}
	};
}
