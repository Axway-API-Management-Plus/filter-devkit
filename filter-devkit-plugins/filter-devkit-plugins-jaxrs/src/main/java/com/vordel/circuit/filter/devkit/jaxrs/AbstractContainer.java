package com.vordel.circuit.filter.devkit.jaxrs;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;

import org.glassfish.jersey.internal.PropertiesDelegate;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spi.Container;

import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.filter.devkit.context.resources.SelectorResource;
import com.vordel.circuit.filter.devkit.script.PolicyHelper;
import com.vordel.circuit.filter.devkit.script.jaxrs.MessagePropertiesDelegate;
import com.vordel.el.Selector;
import com.vordel.mime.Body;
import com.vordel.mime.Headers;

public abstract class AbstractContainer<T extends AbstractWebComponent> implements Container {
	@Override
	public final ResourceConfig getConfiguration() {
		AbstractWebComponent component = getWebComponent();

		return component == null ? null : component.getResourceConfiguration();
	}

	@Override
	public final ApplicationHandler getApplicationHandler() {
		AbstractWebComponent component = getWebComponent();

		return component == null ? null : component.getApplicationHandler();
	}

	@Override
	public final void reload() {
		ResourceConfig configuration = getConfiguration();

		reload(configuration);
	}

	public final void reload(Application application) {
		ResourceConfig configuration = application == null ? null : ResourceConfig.forApplication(application);

		reload(configuration);
	}

	protected abstract T getWebComponent();

	private static final Selector<Headers> REQUEST_HEADERS = SelectorResource.fromExpression(MessageProperties.HTTP_HEADERS, Headers.class);
	private static final Selector<String> REQUEST_VERB = SelectorResource.fromExpression(MessageProperties.HTTP_REQ_VERB, String.class);
	private static final Selector<Body> REQUEST_BODY = SelectorResource.fromExpression(MessageProperties.CONTENT_BODY, Body.class);

	private static URI appendSlash(URI uri) {
		UriBuilder builder = UriBuilder.fromUri(uri);
		StringBuilder path = new StringBuilder();

		path.append(uri.getPath());
		path.append('/');
		builder = builder.replacePath(path.toString());

		return builder.build();
	}

	public static final ContainerRequest createContainerRequest(Message message) throws IOException {
		SecurityContext securityContext = new MessageSecurityContext(message);
		Headers headers = REQUEST_HEADERS.substitute(message);
		URI requestURI = PolicyHelper.getRequestURI(message, headers);
		String verb = REQUEST_VERB.substitute(message);
		ContainerRequest request = null;

		if (requestURI != null) {
			Map<String, Object> properties = new HashMap<String, Object>();
			URI baseURI = PolicyHelper.getBaseURI(message, requestURI);

			request = createContainerRequest(message, baseURI, requestURI, verb, securityContext, new MessagePropertiesDelegate(properties));
		}

		return request;
	}

	public static final ContainerRequest createContainerRequest(Message message, URI baseURI, URI requestURI, String verb, SecurityContext securityContext, PropertiesDelegate properties) throws IOException {
		Headers headers = REQUEST_HEADERS.substitute(message);
		ContainerRequest request = null;
		Body body = null;

		if ((verb != null) && (requestURI != null)) {
			/* base uri must end with a '/' */
			if (!baseURI.getPath().endsWith("/")) {
				/* check if we need to append '/' on request URI */
				if (baseURI.getPath().equals(requestURI.getPath())) {
					requestURI = appendSlash(requestURI);
				}

				baseURI = appendSlash(baseURI);
			}

			body = REQUEST_BODY.substitute(message);
			request = new ContainerRequest(baseURI, requestURI, verb, securityContext, properties, null);

			MultivaluedHeaderMap.fromHeaders(request.getRequestHeaders(), headers);

			if (body != null) {
				MultivaluedHeaderMap.fromHeaders(request.getRequestHeaders(), body.getHeaders());

				request.setEntityStream(body.getInputStream(Body.RETAIN_XFER_ENCODING_ON_READ));
			}
		}

		return request;
	}

	private static boolean equals(Object o1, Object o2) {
		return o1 == null ? o2 == null : o1.equals(o2);
	}

	public static class MessagePrincipal implements Principal {
		private final String subjectName;

		protected MessagePrincipal(String subjectName) {
			this.subjectName = subjectName;
		}

		public String getName() {
			return subjectName;
		}

		@Override
		public int hashCode() {
			String subjectName = getName();

			return subjectName == null ? 0 : subjectName.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			boolean equals = o == this;

			if ((!equals) && (o instanceof MessagePrincipal)) {
				equals = AbstractContainer.equals(getName(), ((MessagePrincipal) o).getName());
			}

			return equals;
		}

		@Override
		public String toString() {
			StringBuilder buffer = new StringBuilder();

			buffer.append("[principal: ");

			if (subjectName != null) {
				buffer.append(subjectName);
			}

			buffer.append("]");

			return buffer.toString();
		}
	}

	public static class MessageSecurityContext implements SecurityContext {
		private static final Selector<String> AUTHN_SUBJECT = SelectorResource.fromExpression(MessageProperties.AUTHN_SUBJECT_ID, String.class);
		private static final Selector<String> AUTHN_SCHEME = SelectorResource.fromExpression("authentication.subject.scheme", String.class);
		private static final Selector<String> PROTOCOL = SelectorResource.fromExpression(MessageProperties.HTTP_REQ_PROTOCOL, String.class);

		private final Message msg;

		private MessagePrincipal principal = null;

		public MessageSecurityContext(Message msg) {
			this.msg = msg;
		}

		@Override
		public Principal getUserPrincipal() {
			/*
			 * resolve the selector ${authentication.subject.id} and generate a principal if
			 * not null
			 */
			String subject = AUTHN_SUBJECT.substitute(msg);

			if (subject != null) {
				if ((principal == null) || (!subject.equals(principal.getName()))) {
					principal = new MessagePrincipal(subject);
				}
			} else {
				principal = null;
			}

			return principal;
		}

		@Override
		public boolean isUserInRole(String role) {
			return false;
		}

		@Override
		public boolean isSecure() {
			/* resolve the selector ${http.request.protocol} and compare against 'https' */
			return "https".equalsIgnoreCase(PROTOCOL.substitute(msg));
		}

		@Override
		public String getAuthenticationScheme() {
			Principal principal = getUserPrincipal();
			String scheme = null;

			if (principal != null) {
				/* if we do have a principal, try to resolve ${authentication.subject.scheme} */
				scheme = AUTHN_SCHEME.substitute(msg);

				if (scheme == null) {
					/* if no scheme available, use BASIC_AUTH as default */
					scheme = SecurityContext.BASIC_AUTH;
				}
			}

			return scheme;
		}
	}
}
