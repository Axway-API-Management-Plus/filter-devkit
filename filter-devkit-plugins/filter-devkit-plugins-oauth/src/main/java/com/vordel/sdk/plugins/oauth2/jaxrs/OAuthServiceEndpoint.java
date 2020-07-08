package com.vordel.sdk.plugins.oauth2.jaxrs;

import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.auth_none;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_invalid_request;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.response_type_code;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.response_type_token;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.jaxrs.VordelBodyProvider;
import com.vordel.circuit.oauth.kps.ApplicationDetails;
import com.vordel.circuit.script.context.resources.PolicyResource;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.config.Circuit;
import com.vordel.el.Selector;
import com.vordel.mime.Body;
import com.vordel.mime.HeaderSet;
import com.vordel.mime.JSONBody;
import com.vordel.sdk.plugins.oauth2.model.OAuthParameters;
import com.vordel.sdk.plugins.oauth2.model.ResponseTypeSet;
import com.vordel.trace.Trace;

public abstract class OAuthServiceEndpoint {
	public static final Charset UTF8 = Charset.forName("UTF-8");
	public static final Charset ISO8859 = Charset.forName("ISO_8859_1");

	protected static final Selector<String> BODY_SELECTOR = SelectorResource.fromExpression(MessageProperties.CONTENT_BODY, String.class);
	protected static final Selector<String> AUTHENTICATED_SUBJECT = SelectorResource.fromExpression(MessageProperties.AUTHN_SUBJECT_ID, String.class);

	protected static final ObjectMapper MAPPER = new ObjectMapper();
	
	public static URI getServiceURI(UriInfo info) {
		URI result = info.getRequestUriBuilder().replaceQuery(null).build();
		String path = result.getPath();
		
		if ((path.length() > 1) && path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
			
			result = UriBuilder.fromUri(result).replacePath(path).build();
		}
		
		return result;
	}

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

	public static boolean isResponseTypeAllowed(ApplicationDetails details, Set<String> response_types) {
		Map<String, String> tags = details.getApplication().getTags();
		boolean allowed = false;

		if (tags != null) {
			Set<String> registered_types = splitCommaSeparatedValues(tags.get("response_types"));

			if (registered_types != null) {
				Set<String> parsed = new HashSet<String>();

				for(String registered : registered_types) {
					Iterator<String> iterator = ResponseTypeSet.iterator(registered);

					while(iterator.hasNext()) {
						parsed.add(iterator.next());
					}
				}

				allowed &= parsed.containsAll(response_types);
				
				if ((!allowed) && response_types.contains(response_type_code)) {
					allowed |= isGrantTypeAllowed(details, "authorization_code");
				}
				
				if ((!allowed) && response_types.contains(response_type_token)) {
					allowed |= isGrantTypeAllowed(details, "implicit");
				}
			}
		}

		return allowed;
	}

	public static boolean isAuthMethodAllowed(ApplicationDetails details, Set<String> auth_methods) {
		Map<String, String> tags = details.getApplication().getTags();
		boolean allowed = false;

		if (tags != null) {
			Set<String> allowed_methods = splitCommaSeparatedValues(tags.get("rfc7591_auth_methods"));

			if (allowed_methods != null) {
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
		}

		return allowed;
	}

	private static final Selector<Integer> HTTP_RESPONSE_STATUS = SelectorResource.fromExpression("http.response.status", Integer.class);

	public static Response toResponse(Message msg) throws IOException {
		Integer status = HTTP_RESPONSE_STATUS.substitute(msg);
		ResponseBuilder builder = headers(Response.status(status == null ? 500 : status.intValue()), (HeaderSet) msg.get(MessageProperties.HTTP_HEADERS));
		Body body = (Body) msg.get(MessageProperties.CONTENT_BODY);

		if (body != null) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			MediaType media = VordelBodyProvider.getMediaType(body);

			builder = headers(builder, body.getHeaders());
			VordelBodyProvider.writeTo(body, out);

			builder.type(media).entity(out.toByteArray());
		}

		return builder.build();
	}

	private static ResponseBuilder headers(ResponseBuilder builder, HeaderSet headers) {
		if (headers != null) {
			Iterator<String> names = headers.getHeaderNames();

			while(names.hasNext()) {
				String name = names.next();

				if (!HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
					Iterator<String> values = headers.getHeaders(name);

					while(values.hasNext()) {
						builder.header(name, values.next());
					}
				}
			}
		}

		return builder;
	}

	protected static Object getResponseJsonEntity(Response response) {
		Object entity = null;

		if (response != null) {
			MediaType mediaType = response.getMediaType();

			if ((mediaType != null) && mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
				entity = response.getEntity();

				try {
					if (entity instanceof JSONBody) {
						entity = ((JSONBody) entity).getJSON();
					} else if (entity instanceof Body) {
						ByteArrayOutputStream ioe = new ByteArrayOutputStream();

						((Body) entity).write(ioe, Body.WRITE_NO_CTE);

						entity = MAPPER.readValue(ioe.toByteArray(), JsonNode.class);
					} else if (entity != null) {
						entity = MAPPER.convertValue(response.getEntity(), JsonNode.class);
					}
				} catch (IOException e) {
					Trace.error("Unable to read Body", e);

					entity = null;
				}
			} else {
				entity = response.getEntity();
			}
		}

		return entity;
	}

	protected abstract Response service(Message msg, Circuit circuit, HttpHeaders headers, Request request, UriInfo info, OAuthParameters parsed, ObjectNode body, MultivaluedMap<String, String> merged);
}
