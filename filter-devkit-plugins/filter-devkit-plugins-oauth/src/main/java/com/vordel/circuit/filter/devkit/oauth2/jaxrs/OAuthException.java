package com.vordel.circuit.filter.devkit.oauth2.jaxrs;

import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_error;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_error_description;
import static com.vordel.circuit.filter.devkit.oauth2.model.OAuthConstants.param_error_uri;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vordel.security.auth.UserUtility;

public class OAuthException extends WebApplicationException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4290673680214412465L;

	private static final ObjectMapper MAPPER = new ObjectMapper();
	
	public OAuthException(String error, String error_uri, String error_description) {
		this(Response.Status.BAD_REQUEST, error, error_uri, error_description);
	}

	public OAuthException(String error, String error_uri, String error_description, Throwable cause) {
		this(Response.Status.BAD_REQUEST, error, error_uri, error_description, cause);
	}

	public OAuthException(Response.Status status, String error, String error_uri, String error_description) {
		this(asResponse(status, error, error_uri, error_description));
	}

	public OAuthException(Response.Status status, String error, String error_uri, String error_description, Throwable cause) {
		this(cause, asResponse(status, error, error_uri, error_description));
	}

	public OAuthException(Throwable cause, Response response) {
		super(cause, response);
	}

	public OAuthException(Response response) {
		super(response);
	}

	private ObjectNode getEntity() {
		Response response = getResponse();

		return (ObjectNode) response.getEntity();
	}

	public String getError() {
		ObjectNode entity = getEntity();

		return entity.path(param_error).asText(null);
	}

	public String getErrorURI() {
		ObjectNode entity = getEntity();

		return entity.path(param_error_uri).asText(null);
	}

	public String getErrorDescription() {
		ObjectNode entity = getEntity();

		return entity.path(param_error_description).asText(null);
	}

	public static Response asResponse(Response.Status status, String error, String error_uri, String error_description) {
		ResponseBuilder builder = Response.status(status);
		ObjectNode entity = MAPPER.createObjectNode();
		
		if (status == Response.Status.UNAUTHORIZED) {
			String realm = UserUtility.getVSUserRealm();

			/* HTTP Signature may also be available, but it is not advertised */
			builder.header(HttpHeaders.WWW_AUTHENTICATE, String.format("Basic realm=\"%s\"", realm));
		}

		if (error != null) {
			entity.put(param_error, error);
		}

		if (error_uri != null) {
			entity.put(param_error_uri, error_uri);
		}

		if (error_description != null) {
			entity.put(param_error_description, error_description);
		}

		builder.type(MediaType.APPLICATION_JSON_TYPE);
		builder.entity(entity);

		return builder.build();
	}
}
