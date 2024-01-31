package com.vordel.circuit.script.bind;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;

import javax.ws.rs.core.UriBuilder;

import org.glassfish.jersey.uri.UriComponent;

import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.el.Selector;
import com.vordel.mime.Headers;
import com.vordel.trace.Trace;

@ExtensionPlugin("policy.helper")
public class PolicyHelper {
	private static final Selector<String> RESOLVED_PATH = SelectorResource.fromExpression(MessageProperties.RESOLVED_TO_PATH, String.class);
	private static final Selector<URI> REQUEST_URI = SelectorResource.fromExpression(MessageProperties.HTTP_REQ_URI, URI.class);
	private static final Selector<String> REQUEST_PROTOCOL = SelectorResource.fromExpression(MessageProperties.HTTP_REQ_PROTOCOL, String.class);
	private static final Selector<String> REQUEST_HOSTNAME = SelectorResource.fromExpression(MessageProperties.HTTP_REQ_HOSTNAME, String.class);
	private static final Selector<InetSocketAddress> REQUEST_LOCALADDR = SelectorResource.fromExpression(MessageProperties.HTTP_REQ_LOCALADDR, InetSocketAddress.class);

	@SubstitutableMethod("RequestURI")
	public static URI getRequestURI(Message msg, @DictionaryAttribute(MessageProperties.HTTP_HEADERS) Headers headers) {
		URI requestURI = REQUEST_URI.substitute(msg);
		String scheme = REQUEST_PROTOCOL.substitute(msg);

		if (scheme != null) {
			String authority = null;

			if (headers != null) {
				Iterator<String> iterator = headers.getHeaders("Host");

				if (iterator != null) {
					while ((authority == null) && iterator.hasNext()) {
						authority = iterator.next();

						if ((authority != null) && authority.isEmpty()) {
							authority = null;
						}
					}
				}
			}

			if (authority == null) {
				String hostName = REQUEST_HOSTNAME.substitute(msg);
				InetSocketAddress address = REQUEST_LOCALADDR.substitute(msg);
				int port = -1;

				if (address != null) {
					port = address.getPort();

					if (hostName == null) {
						hostName = address.getHostString();
					}
				}

				if (hostName != null) {
					authority = String.format("%s:%d", hostName.toLowerCase(), port);
				}
			}

			if ((authority != null) && (requestURI != null)) {
				String query = requestURI.getRawQuery();

				try {
					requestURI = new URI(scheme, authority, requestURI.getPath(), null, null);

					if ("http".equalsIgnoreCase(scheme) && (requestURI.getPort() == 80)) {
						requestURI = new URI(scheme, requestURI.getUserInfo(), requestURI.getHost(), -1, requestURI.getPath(), null, null);
						authority = requestURI.getAuthority();
					} else if ("https".equalsIgnoreCase(scheme) && (requestURI.getPort() == 443)) {
						requestURI = new URI(scheme, requestURI.getUserInfo(), requestURI.getHost(), -1, requestURI.getPath(), null, null);
						authority = requestURI.getAuthority();
					}

					if ((query != null) && query.isEmpty()) {
						query = null;
					}

					if (query != null) {
						requestURI = new URI(String.format("%s?%s", requestURI.toASCIIString(), query));
					}

					requestURI = requestURI.normalize();
				} catch (URISyntaxException e) {
					Trace.error("can't produce request URI");
				}
			}
		}

		return requestURI;
	}

	private static URI getLocalURI(Message msg, URI requestURI, String basePath) {
		URI baseURI = null;

		if ((basePath != null) && (requestURI != null)) {
			UriBuilder builder = UriBuilder.fromUri(requestURI);

			basePath = UriComponent.encode(basePath, UriComponent.Type.PATH);
			baseURI = builder.replacePath(basePath).replaceQuery(null).build();
		}

		return baseURI == null ? null : baseURI.normalize();
	}

	public static URI getLocalURI(Message msg, String basePath) {
		Headers headers = msg == null ? null : (Headers) msg.get(MessageProperties.HTTP_HEADERS);
		URI requestURI = getRequestURI(msg, headers);

		return getLocalURI(msg, requestURI, basePath);
	}

	@SubstitutableMethod("BaseURI")
	public static URI getBaseURI(Message msg, @SelectorExpression("extensions[\"policy.helper\"].RequestURI") URI requestURI) {
		URI baseURI = null;

		if (requestURI != null) {
			String basePath = RESOLVED_PATH.substitute(msg);

			baseURI = getLocalURI(msg, requestURI, basePath);
		}

		return baseURI;
	}
}
