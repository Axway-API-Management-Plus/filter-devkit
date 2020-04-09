package com.vordel.sdk.plugins.oauth2.jaxrs;

import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.auth_none;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_invalid_request;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.oauth.kps.ApplicationDetails;
import com.vordel.circuit.script.context.resources.PolicyResource;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.config.Circuit;
import com.vordel.el.Selector;
import com.vordel.sdk.plugins.oauth2.model.OAuthParameters;

public abstract class OAuthServiceEndpoint {
	public static final Charset UTF8 = Charset.forName("UTF-8");
	public static final Charset ISO8859 = Charset.forName("ISO_8859_1");

	protected static final Selector<String> BODY_SELECTOR = SelectorResource.fromExpression(MessageProperties.CONTENT_BODY, String.class);
	protected static final Selector<String> AUTHENTICATED_SUBJECT = SelectorResource.fromExpression(MessageProperties.AUTHN_SUBJECT_ID, String.class);

	protected static final ObjectMapper MAPPER = new ObjectMapper();

	public static final boolean isValidPolicy(PolicyResource resource) {
		return (resource != null) && (resource.getCircuit() != null);
	}

	public static final boolean invokePolicy(Message msg, Circuit circuit, PolicyResource resource) throws CircuitAbortException {
		try {
			return resource.invoke(circuit, msg);
		} finally {
			Object exception = msg.get("oauth.exception");

			if (exception instanceof OAuthException) {
				throw (OAuthException) exception;
			}
		}
	}

	protected static final Iterable<String> getMultipleValueParameter(String key, MultivaluedMap<String, String> merged) {
		List<String> values = merged == null ? null : merged.get(key);

		if (values == null) {
			values = Collections.emptyList();
		}

		return values;
	}

	protected static final String getSingleValueParameter(String key, JsonNode value, MultivaluedMap<String, String> merged) {
		return getSingleValueParameter(key, value == null ? null : value.asText(null), merged);
	}

	protected static final String getSingleValueParameter(String key, String value, MultivaluedMap<String, String> merged) {
		for (String cursor : getMultipleValueParameter(key, merged)) {
			if (value == null) {
				value = cursor;
			} else if ((value != null) && (!value.equals(cursor))) {
				throw new OAuthException(err_invalid_request, null, String.format("duplicate parameter '%s'", key));
			}
		}

		return value;
	}

	protected static MultivaluedMap<String, String> cleanupHeaders(MultivaluedMap<String, String> merged) {
		Iterator<Entry<String, List<String>>> entries = merged.entrySet().iterator();

		while (entries.hasNext()) {
			Entry<String, List<String>> entry = entries.next();

			if (entry == null) {
				entries.remove();
			} else {
				String key = entry.getKey();

				if ((key == null) || key.isEmpty() || (entry.getValue() == null)) {
					entries.remove();
				} else {
					Set<String> used = new HashSet<String>();
					Iterator<String> values = entry.getValue().iterator();

					while (values.hasNext()) {
						String value = values.next();

						if ((value == null) || value.isEmpty() || (!used.add(value))) {
							values.remove();
						}
					}

					if (used.isEmpty()) {
						entries.remove();
					}
				}
			}
		}

		return merged;
	}

	private static Set<String> splitCommaSeparatedValues(String value) {
		Set<String> splitted = null;

		if (value != null) {
			String[] values = value.split(",");

			splitted = new HashSet<String>();

			for (String flow : values) {
				if ((flow != null) && (!flow.isEmpty())) {
					splitted.add(flow);
				}
			}
		}

		return splitted;
	}

	public static boolean isGrantTypeAllowed(ApplicationDetails details, String grant_type) {
		Map<String, String> tags = details.getApplication().getTags();
		boolean allowed = false;

		if (tags != null) {
			Set<String> grant_types = splitCommaSeparatedValues(tags.get("rfc7591_grant_types"));

			allowed &= (grant_types != null) && grant_types.contains(grant_type);
		}

		return allowed;
	}

	public static boolean isAuthMethodAllowed(ApplicationDetails details, Set<String> auth_methods) {
		Map<String, String> tags = details.getApplication().getTags();
		boolean allowed = false;

		if (tags != null) {
			Set<String> allowed_methods = splitCommaSeparatedValues(tags.get("rfc7591_auth_methods"));

			if (auth_methods.isEmpty()) {
				allowed |= allowed_methods.contains(auth_none);
			} else {
				Iterator<String> iterator = auth_methods.iterator();
				
				while((!allowed) && iterator.hasNext()) {
					String used_method = iterator.next();

					allowed |= allowed_methods.contains(used_method);
				}
			}
		}

		return allowed;
	}

	protected abstract Response service(Message msg, Circuit circuit, HttpHeaders headers, Request request, UriInfo info, OAuthParameters parsed, ObjectNode body, MultivaluedMap<String, String> merged);
}
