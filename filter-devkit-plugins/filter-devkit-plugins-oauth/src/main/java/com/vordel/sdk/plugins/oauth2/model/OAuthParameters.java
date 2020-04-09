package com.vordel.sdk.plugins.oauth2.model;

import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_actor_token;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_actor_token_type;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_assertion;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_audience;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_client_assertion;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_client_assertion_type;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_client_id;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_client_secret;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_code;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_code_verifier;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_grant_type;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_password;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_redirect_uri;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_requested_token_type;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_resource;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_scope;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_subject_token;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_subject_token_type;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_username;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiFunction;

import javax.ws.rs.core.MultivaluedMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vordel.mime.Headers;

public class OAuthParameters extends AbstractMap<String, Object> {
	private final Map<String, OAuthParameter<?>> descriptors;
	private final ObjectNode root;

	private final Set<String> keySet;
	private final Set<Entry<String, Object>> entrySet;
	private final HashSet<String> parsed;
	static final Map<String, OAuthParameter<?>> DESCRIPTORS_MAP = descriptors();

	public OAuthParameters(ObjectNode root) {
		this(root, new HashMap<String, OAuthParameter<?>>());
	}

	public OAuthParameters(ObjectNode root, Map<String, OAuthParameter<?>> descriptors) {
		this.root = root;
		this.descriptors = descriptors;
		this.parsed = new HashSet<String>();

		this.keySet = new AbstractSet<String>() {
			@Override
			public Iterator<String> iterator() {
				return new KeyIterator(descriptors, getObjectNode());
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
		};

		this.entrySet = new AbstractSet<Entry<String, Object>>() {
			@Override
			public Iterator<Entry<String, Object>> iterator() {
				return new EntryIterator(descriptors, getObjectNode());
			}

			@Override
			public int size() {
				return keySet().size();
			}
		};
	}

	public Map<String, OAuthParameter<?>> getDescriptors() {
		return descriptors;
	}
	
	public Set<String> getParsedParameters() {
		return parsed;
	}

	public ObjectNode getObjectNode() {
		return root;
	}
	
	public <H extends Headers> H toQueryString(H query) {
		DescriptorIterator iterator = new DescriptorIterator(descriptors, getObjectNode());
		
		while(iterator.hasNext()) {
			query = iterator.toQueryString(query);
		}
		
		return query;
	}

	@Override
	public int size() {
		return keySet.size();
	}

	@Override
	public boolean containsValue(Object value) {
		return values().contains(value);
	}

	@Override
	public boolean containsKey(Object key) {
		return (key instanceof String) && containsKey((String) key, descriptors, getObjectNode());
	}

	@Override
	public Object get(Object key) {
		return key instanceof String ? get((String) key, descriptors, getObjectNode(), false) : null;
	}

	public JsonNode parse(String key, String value, BiFunction<String, String, String> validator) {
		OAuthParameter<?> descriptor = descriptors == null ? null : descriptors.get(key);
		JsonNode result = MissingNode.getInstance();

		if (descriptor != null) {
			result = descriptor.parse(getObjectNode(), key, value, null, validator);
			
			parsed.add(key);
		}

		return result;
	}

	public JsonNode parse(String key, ObjectNode body, MultivaluedMap<String, String> merged, BiFunction<String, String, String> validator) {
		OAuthParameter<?> descriptor = descriptors == null ? null : descriptors.get(key);
		JsonNode result = MissingNode.getInstance();

		if (descriptor != null) {
			result = descriptor.parse(getObjectNode(), key, body, merged, validator);
			
			parsed.add(key);
		}

		return result;
	}

	@Override
	public Object remove(Object key) {
		return key instanceof String ? get((String) key, descriptors, getObjectNode(), true) : null;
	}

	@Override
	public Set<Entry<String, Object>> entrySet() {
		return entrySet;
	}

	public static void register(OAuthParameters parsed, String parameter) {
		if (parsed != null) {
			OAuthParameter<?> descriptor = DESCRIPTORS_MAP.get(parameter);
	
			if (descriptor != null) {
				parsed.getDescriptors().put(parameter, descriptor);
			}
		}
	}

	public static void register(OAuthParameters parsed, Iterable<String> parameters) {
		if (parameters != null) {
			for (String parameter : parameters) {
				register(parsed, parameter);
			}
		}
	}

	private static Map<String, OAuthParameter<?>> descriptors() {
		Map<String, OAuthParameter<?>> descriptors = new HashMap<String, OAuthParameter<?>>();
	
		descriptors.put(param_client_id, OAuthParameter.CLIENT_ID);
		descriptors.put(param_client_secret, OAuthParameter.GENERIC_SINGLE);
		descriptors.put(param_client_assertion, OAuthParameter.GENERIC_SINGLE);
		descriptors.put(param_client_assertion_type, OAuthParameter.GENERIC_SINGLE);
	
		descriptors.put(param_grant_type, OAuthParameter.GENERIC_SINGLE);
		descriptors.put(param_scope, OAuthParameter.SCOPES);
	
		descriptors.put(param_code, OAuthParameter.GENERIC_SINGLE);
		descriptors.put(param_redirect_uri, OAuthParameter.GENERIC_SINGLE);
		descriptors.put(param_code_verifier, OAuthParameter.GENERIC_SINGLE);
	
		descriptors.put(param_username, OAuthParameter.GENERIC_SINGLE);
		descriptors.put(param_password, OAuthParameter.GENERIC_SINGLE);
	
		descriptors.put(param_assertion, OAuthParameter.GENERIC_SINGLE);
	
		descriptors.put(param_resource, OAuthParameter.GENERIC_SINGLE);
		descriptors.put(param_audience, OAuthParameter.GENERIC_SINGLE);
		descriptors.put(param_requested_token_type, OAuthParameter.GENERIC_SINGLE);
		descriptors.put(param_subject_token, OAuthParameter.GENERIC_SINGLE);
		descriptors.put(param_subject_token_type, OAuthParameter.GENERIC_SINGLE);
		descriptors.put(param_actor_token, OAuthParameter.GENERIC_SINGLE);
		descriptors.put(param_actor_token_type, OAuthParameter.GENERIC_SINGLE);
	
		return Collections.unmodifiableMap(descriptors);
	}

	private static boolean containsKey(String key, Map<String, OAuthParameter<?>> descriptors, ObjectNode root) {
		OAuthParameter<?> descriptor = descriptors == null ? null : descriptors.get(key);

		return (descriptor != null) && descriptor.contained(root, key);
	}

	private static Object get(String key, Map<String, OAuthParameter<?>> descriptors, ObjectNode root, boolean remove) {
		OAuthParameter<?> descriptor = descriptors == null ? null : descriptors.get(key);
		Object result = null;

		if ((descriptor != null) && descriptor.contained(root, key)) {
			result = descriptor.get(root, key);

			if (remove) {
				root.remove(key);
			}
		}

		return result;
	}

	private static <T> Iterator<T> emptyIterator() {
		Set<T> empty = Collections.emptySet();

		return empty.iterator();
	}

	public static class KeyIterator implements Iterator<String> {
		private final DescriptorIterator iterator;

		public KeyIterator(Map<String, OAuthParameter<?>> descriptors, ObjectNode root) {
			this(root == null ? emptyIterator() : root.fieldNames(), descriptors, root);
		}

		public KeyIterator(Iterator<String> keys, Map<String, OAuthParameter<?>> descriptors, ObjectNode root) {
			this.iterator = new DescriptorIterator(keys, descriptors, root);
		}

		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override
		public String next() {
			return iterator.nextKey();
		}

		@Override
		public void remove() {
			iterator.remove();
		}
	}

	public static class EntryIterator implements Iterator<Entry<String, Object>> {
		private final DescriptorIterator iterator;

		public EntryIterator(Map<String, OAuthParameter<?>> descriptors, ObjectNode root) {
			this(root == null ? emptyIterator() : root.fieldNames(), descriptors, root);
		}

		public EntryIterator(Iterator<String> keys, Map<String, OAuthParameter<?>> descriptors, ObjectNode root) {
			this.iterator = new DescriptorIterator(keys, descriptors, root);
		}

		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override
		public Entry<String, Object> next() {
			return iterator.nextEntry();
		}

		@Override
		public void remove() {
			iterator.remove();
		}
	}

	public static class DescriptorIterator implements Iterator<OAuthParameter<?>> {
		private final Map<String, OAuthParameter<?>> descriptors;
		private final ObjectNode root;
		private final Iterator<String> keys;

		private OAuthParameter<?> next = null;
		private String name = null;

		private boolean remove = false;

		public DescriptorIterator(Map<String, OAuthParameter<?>> descriptors, ObjectNode root) {
			this(root == null ? emptyIterator() : root.fieldNames(), descriptors, root);
		}

		public DescriptorIterator(Iterator<String> keys, Map<String, OAuthParameter<?>> descriptors, ObjectNode root) {
			this.descriptors = descriptors;
			this.root = root;
			this.keys = keys;
		}

		@Override
		public boolean hasNext() {
			if (next == null) {
				remove = false;

				while ((next == null) && keys.hasNext()) {
					String cursor = keys.next();

					if (cursor != null) {
						OAuthParameter<?> descriptor = descriptors == null ? null : descriptors.get(cursor);

						if ((descriptor != null) && descriptor.contained(root, cursor)) {
							next = descriptor;
							name = cursor;
						}
					}
				}
			}

			return next != null;
		}

		@Override
		public OAuthParameter<?> next() {
			try {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}

				return next;
			} finally {
				next = null;
				name = null;
				remove = true;
			}
		}

		public String nextKey() {
			try {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}

				return name;
			} finally {
				next = null;
				name = null;
				remove = true;
			}
		}

		public Object nextValue() {
			try {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}

				return next.get(root, name);
			} finally {
				next = null;
				name = null;
				remove = true;
			}
		}

		public Entry<String, Object> nextEntry() {
			try {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}

				OAuthParameter<?> descriptor = next;
				String key = name;

				return new Entry<String, Object>() {

					@Override
					public String getKey() {
						return key;
					}

					@Override
					public Object getValue() {
						return descriptor.get(root, key);
					}

					@Override
					public Object setValue(Object value) {
						if (!(value instanceof String)) {
							throw new IllegalArgumentException();
						}

						return descriptor.put(root, name, (String) value);
					}

				};
			} finally {
				next = null;
				name = null;
				remove = true;
			}
		}
		
		public <H extends Headers> H toQueryString(H query) {
			try {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}

				
				OAuthParameter<?> descriptor = next;
				String key = name;

				return descriptor.toQueryString(root, key, query);
			} finally {
				next = null;
				name = null;
			}
		}

		@Override
		public void remove() {
			if (!remove) {
				throw new IllegalStateException();
			}

			remove = false;
			keys.remove();
		}
	}
}
