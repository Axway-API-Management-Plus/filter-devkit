package com.vordel.sdk.plugins.oauth2.jaxrs;

import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.auth_client_secret_basic;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.auth_client_secret_post;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_invalid_request;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_rfc6749_invalid_client;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.err_rfc6749_server_error;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_client_assertion;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_client_assertion_type;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_client_id;
import static com.vordel.sdk.plugins.oauth2.model.OAuthConstants.param_client_secret;

import java.io.IOException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.Form;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.oauth.kps.ApplicationDetails;
import com.vordel.circuit.script.context.resources.PolicyResource;
import com.vordel.config.Circuit;
import com.vordel.sdk.plugins.oauth2.OAuthGuavaCache;
import com.vordel.sdk.plugins.oauth2.model.OAuthParameters;
import com.vordel.trace.Trace;

public abstract class OAuthAuthenticatedEndpoint extends OAuthServiceEndpoint {
	private static final Pattern AUTHORIZATION_ANY_MATCHER = Pattern.compile("\\s*(\\S+)\\s*.*", Pattern.CASE_INSENSITIVE);
	/**
	 * Basic HTTP Authentication scheme matcher.
	 */
	private static final Pattern AUTHORIZATION_BASIC_MATCHER = Pattern.compile("\\s*Basic\\s+(\\S+)\\s*", Pattern.CASE_INSENSITIVE);
	/**
	 * Username/Password pattern. Empty passwords NOT Allowed
	 */
	private static final Pattern CREDENTIAL_MATCHER = Pattern.compile("([^:]+):([^:]+)", Pattern.CASE_INSENSITIVE);

	public abstract PolicyResource getClientAssertionValidator();

	public abstract PolicyResource getClientAuthorizationValidator();

	protected abstract boolean enableClientSecretQueryCheck(Message msg);

	protected abstract boolean enableFormClientAuth(Message msg);

	protected abstract boolean enableBasicClientAuth(Message msg);

	protected abstract boolean enableClientAuthFilter(Message msg);

	protected abstract boolean forceClientSecret(Message msg);

	/**
	 * parse http basic authorization and return error if any
	 * 
	 * @param headers incoming http headers
	 * @return
	 * @throws CircuitAbortException
	 */
	protected MultivaluedMap<String, String> authorization(Circuit circuit, Message msg, HttpHeaders headers) throws CircuitAbortException {
		String authorization = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
		Matcher matcher = null;

		if ((authorization != null) && (matcher = AUTHORIZATION_ANY_MATCHER.matcher(authorization)).matches()) {
			String scheme = matcher.group(1);

			if ("Basic".equalsIgnoreCase(scheme)) {
				String encoded = (matcher = AUTHORIZATION_BASIC_MATCHER.matcher(authorization)).matches() ? matcher.group(1) : null;

				if ((encoded != null) && (encoded.length() > 0)) {
					try {
						String decoded = new String(Base64.getDecoder().decode(encoded), UTF8);

						if ((decoded != null) && (matcher = CREDENTIAL_MATCHER.matcher(decoded)).matches()) {
							String login = matcher.group(1);

							if ((login != null) && (login.length() == 0)) {
								login = null;
							}

							if (login != null) {
								String password = matcher.group(2);

								if ((password != null) && (password.length() > 0)) {
									MultivaluedMap<String, String> result = new Form().asMap();

									result.add(param_client_id, login);
									result.add(param_client_secret, password);

									return result;
								} else {
									throw new OAuthException(Response.Status.UNAUTHORIZED, err_invalid_request, null, "When using Basic authorization, both client_id and client_secret must be used");
								}
							}
						}
					} catch (OAuthException e) {
						throw e;
					} catch (Exception e) {
						throw new OAuthException(Response.Status.UNAUTHORIZED, err_invalid_request, null, "Bad authorization syntax", e);
					}
				}

				throw new OAuthException(Response.Status.UNAUTHORIZED, err_invalid_request, null, "Bad authorization syntax");
			} else if (!scheme.isEmpty()) {
				PolicyResource signatureValidator = getClientAuthorizationValidator();

				if ((!isValidPolicy(signatureValidator))) {
					throw new OAuthException(err_invalid_request, null, String.format("%s authorization is not supported", scheme));
				}

				if (!invokePolicy(msg, circuit, signatureValidator)) {
					/* trace error, but report invalid credentails */
					Trace.error("Provided http authorization is invalid, return unmodified message");

					try {
						/* return current message without modification */
						throw new OAuthException(toResponse(msg));
					} catch (IOException e) {
						throw new OAuthException(Response.Status.INTERNAL_SERVER_ERROR, err_rfc6749_server_error, null, "unexpected error occured", e);
					}
				}

				String login = AUTHENTICATED_SUBJECT.substitute(msg);

				if ((login != null) && (login.length() == 0)) {
					login = null;
				}

				if (login == null) {
					throw new OAuthException(Response.Status.UNAUTHORIZED, err_rfc6749_invalid_client, null, "invalid credentials");
				}

				MultivaluedMap<String, String> result = new Form().asMap();

				result.add(param_client_id, login);

				return result;
			} else {
				throw new OAuthException(Response.Status.UNAUTHORIZED, err_invalid_request, null, "Authentication Scheme not available");
			}
		}

		return new Form().asMap();
	}

	protected ApplicationDetails parseAuthenticationParameters(Circuit circuit, Message msg, HttpHeaders headers, Request request, UriInfo info, OAuthParameters parsed, ObjectNode body, MultivaluedMap<String, String> merged) throws CircuitAbortException {
		MultivaluedMap<String, String> authorization = authorization(circuit, msg, headers);

		Set<String> authentication_methods = new HashSet<String>();

		msg.put("oauth.request.authentication.methods", authentication_methods);

		String client_secret = null;

		if (enableClientSecretQueryCheck(msg)) {
			/* check if the client_secret is in query */
			client_secret = getSingleValueParameter(param_client_secret, (String) null, info.getQueryParameters());

			if ((client_secret != null) && (!client_secret.isEmpty())) {
				throw new OAuthException(err_invalid_request, null, "client_secret parameter is not allowed in query");
			}
		}

		/* parse secret from authorization header */
		String authorization_secret = getSingleValueParameter(param_client_secret, (String) null, authorization);

		if (authorization_secret != null) {
			authentication_methods.add(auth_client_secret_basic);

			if (!enableBasicClientAuth(msg)) {
				throw new OAuthException(err_invalid_request, null, "http basic authentication is not allowed");
			}
		}

		/* parse secret from form body */
		client_secret = parsed.parse(param_client_secret, body, merged, null).asText(null);

		if (client_secret == null) {
			client_secret = authorization_secret;
		} else if (authorization_secret != null) {
			throw new OAuthException(err_invalid_request, null, "client_secret parameter conflicts with authorization header");
		} else {
			authentication_methods.add(auth_client_secret_post);

			if (!enableFormClientAuth(msg)) {
				throw new OAuthException(err_invalid_request, null, "form based authentication is not allowed");
			}
		}

		String authorization_id = getSingleValueParameter(param_client_id, (String) null, authorization);
		String client_id = parsed.parse(param_client_id, body, merged, null).asText(null);

		if (client_id == null) {
			client_id = authorization_id;

			parsed.getObjectNode().put(param_client_id, client_id);
		} else if ((authorization_id != null) && (!client_id.equals(authorization_id))) {
			throw new OAuthException(err_invalid_request, null, "client_id parameter conflicts with authorization header");
		}

		String client_assertion_type = parsed.parse(param_client_assertion_type, body, merged, null).asText(null);
		String client_assertion = parsed.parse(param_client_assertion, body, merged, (key, value) -> {
			if ((value == null) || value.isEmpty()) {
				value = null;
			}

			return value;
		}).asText(null);

		if (client_assertion != null) {
			/* sanity check against previous provided credentials */
			if (!authorization.isEmpty()) {
				throw new OAuthException(err_invalid_request, null, "client_assertion parameter conflicts with authorization header");
			} else if (parsed.containsKey(param_client_secret)) {
				throw new OAuthException(err_invalid_request, null, "client_assertion parameter conflicts with client_secret parameter");
			}

			if ((client_assertion_type == null) || client_assertion_type.isEmpty()) {
				throw new OAuthException(err_invalid_request, null, "client_assertion parameter requires client_assertion_type");
			}

			/*
			 * we have a client assertion, validate it using a policy call. client assertion
			 * is available under ${oauth.request.parsed.mapped["client_assertion"]} and
			 * client assertion_type under
			 * ${oauth.request.parsed.mapped["client_assertion_type"]}. if successfull, the
			 * Policy MUST add matching authentication methods to the Set<String> available
			 * in attribute 'oauth.request.authentication.methods'
			 */
			PolicyResource assertionValidator = getClientAssertionValidator();

			if ((!isValidPolicy(assertionValidator))) {
				throw new OAuthException(err_invalid_request, null, "client_assertion parameter is not supported");
			}

			if (!invokePolicy(msg, circuit, assertionValidator)) {
				throw new OAuthException(err_rfc6749_invalid_client, null, "client_assertion is invalid");
			}

			String subject = AUTHENTICATED_SUBJECT.substitute(msg);

			if (subject == null) {
				throw new OAuthException(err_rfc6749_invalid_client, null, "client_assertion is invalid");
			}

			if ((client_id != null) && (!client_id.equals(subject))) {
				throw new OAuthException(err_invalid_request, null, "client_assertion subject mismatch with client_id (RFC7521 section 4.2)");
			}

			parsed.getObjectNode().put(param_client_id, client_id = subject);
		}

		if (client_id == null) {
			throw new OAuthException(err_invalid_request, null, "unable to parse or retrieve client_id");
		}

		ApplicationDetails details = OAuthGuavaCache.getAppDetailsFromClientId(msg, client_id);

		if (details == null) {
			/* trace error, but report invalid credentails */
			Trace.error(String.format("no registered client for client_id '%s'", client_id));

			throw new OAuthException(authorization.isEmpty() ? Response.Status.FORBIDDEN : Response.Status.UNAUTHORIZED, err_rfc6749_invalid_client, null, "invalid credentials");
		}

		if (enableClientAuthFilter(msg) && (!isAuthMethodAllowed(details, authentication_methods))) {
			/* trace error, but report invalid credentails */
			Trace.error(String.format("the client '%s' did not authenticate using a valid registered method", client_id));

			throw new OAuthException(authorization.isEmpty() ? Response.Status.FORBIDDEN : Response.Status.UNAUTHORIZED, err_rfc6749_invalid_client, null, "invalid credentials");
		}

		/*
		 * ensure confidential clients provided credentials to access service.
		 */
		if ((client_secret == null) && (client_assertion == null)) {
			if ("confidential".equalsIgnoreCase(details.getClientType())) {
				/* trace error, but report invalid credentails */
				Trace.error(String.format("client '%s' is confidential and did not provide a credential", client_id));

				throw new OAuthException(authorization.isEmpty() ? Response.Status.FORBIDDEN : Response.Status.UNAUTHORIZED, err_rfc6749_invalid_client, null, "invalid credentials");
			} else if (forceClientSecret(msg)) {
				Trace.error(String.format("client '%s' is not confidential but a credential MUST be provided", client_id));

				throw new OAuthException(authorization.isEmpty() ? Response.Status.FORBIDDEN : Response.Status.UNAUTHORIZED, err_rfc6749_invalid_client, null, "invalid credentials");
			}
		}

		if ((client_secret != null) && (!client_secret.equals(details.getClientSecret()))) {
			/* trace error, but report invalid credentails */
			Trace.error(String.format("client '%s' provided an invalid client_secret", client_id));

			throw new OAuthException(authorization.isEmpty() ? Response.Status.FORBIDDEN : Response.Status.UNAUTHORIZED, err_rfc6749_invalid_client, null, "invalid credentials");
		}

		return details;
	}
}
