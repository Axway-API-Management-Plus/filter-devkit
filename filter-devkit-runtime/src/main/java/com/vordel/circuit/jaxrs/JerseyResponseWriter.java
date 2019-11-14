package com.vordel.circuit.jaxrs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.message.internal.NullOutputStream;
import org.glassfish.jersey.server.ContainerException;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.spi.ContainerResponseWriter;

import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.dwe.http.HTTPPlugin;
import com.vordel.mime.Body;
import com.vordel.mime.ContentType;
import com.vordel.mime.HeaderSet;

public class JerseyResponseWriter implements ContainerResponseWriter {
	private final Message message;

	private boolean committed = false;
	private boolean matched = false;

	public JerseyResponseWriter(Message message) {
		this.message = message;
	}

	/**
	 * indicate at least a method has matched
	 */
	public void reportMatch() {
		matched |= true;
	}

	public boolean matched() {
		return matched;
	}

	@Override
	public OutputStream writeResponseStatusAndHeaders(long contentLength, ContainerResponse response) throws ContainerException {
		OutputStream writer = null;

		if (matched()) {
			MediaType mediaType = response.getMediaType();
			int status = response.getStatus();

			message.put(MessageProperties.HTTP_RSP_STATUS, status);
			message.put(MessageProperties.HTTP_RSP_INFO, HTTPPlugin.getResponseText(status));

			/* in Jersey string conversion we trust !!! */
			MultivaluedMap<String, String> responseHeaders = response.getStringHeaders();
			HeaderSet messageHeaders = MultivaluedHeaderMap.toHeaders(new HeaderSet(), responseHeaders);
			HeaderSet bodyHeaders = messageHeaders.separateContentHeaders();

			message.put(MessageProperties.HTTP_HEADERS, messageHeaders);

			if (mediaType != null) {
				if (response.hasEntity()) {
					writer = new BodyWriterOutputStream(message, mediaType, bodyHeaders);
				} else {
					writer = new NullOutputStream();
				}
			} else {
				message.remove(MessageProperties.CONTENT_BODY);
			}
		} else if (response.hasEntity()) {
			writer = new NullOutputStream();
		}

		return writer;
	}

	@Override
	public boolean suspend(long timeOut, TimeUnit timeUnit, TimeoutHandler timeoutHandler) {
		return true;
	}

	@Override
	public void setSuspendTimeout(long timeOut, TimeUnit timeUnit) throws IllegalStateException {
	}

	@Override
	public void commit() {
		if (!committed) {
			committed |= true;
		}
	}

	@Override
	public void failure(Throwable error) {
		try {
			if (matched() && (!committed)) {
				int status = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();

				message.put(MessageProperties.HTTP_RSP_STATUS, status);
				message.put(MessageProperties.HTTP_RSP_INFO, HTTPPlugin.getResponseText(status));

				message.remove(MessageProperties.CONTENT_BODY);
				message.put(MessageProperties.HTTP_HEADERS, new HeaderSet());
			}
		} finally {
			rethrow(error);
		}
	}

	/**
	 * Rethrow the original exception as required by JAX-RS, 3.3.4
	 *
	 * @param error throwable to be re-thrown
	 */
	private void rethrow(final Throwable error) {
		if (error instanceof RuntimeException) {
			throw (RuntimeException) error;
		} else {
			throw new ContainerException(error);
		}
	}

	@Override
	public boolean enableResponseBuffering() {
		/* no buffering since we are writing in Message */
		return false;
	}

	public static class BodyWriterOutputStream extends OutputStream {
		private final ByteArrayOutputStream baos;
		private final Message message;

		private final HeaderSet bodyHeaders;
		private final ContentType contentType;

		private boolean closed = false;

		public BodyWriterOutputStream(Message message, MediaType mediaType, HeaderSet bodyHeaders) {
			ContentType contentType = null;

			if (mediaType != null) {
				String subType = mediaType.getSubtype();

				/*
				 * produce a content type known by APIGateway, but keep real media type in
				 * headers
				 */
				if (subType.endsWith("+xml")) {
					MediaType xmlType = new MediaType(mediaType.getType(), "xml", mediaType.getParameters());

					contentType = VordelBodyProvider.asContentType(xmlType);
				} else if (subType.endsWith("+json")) {
					MediaType jsonType = new MediaType(mediaType.getType(), "json", mediaType.getParameters());

					contentType = VordelBodyProvider.asContentType(jsonType);
				} else {
					contentType = VordelBodyProvider.asContentType(mediaType);
				}
			}

			this.baos = new ByteArrayOutputStream();
			this.message = message;
			this.bodyHeaders = bodyHeaders;
			this.contentType = contentType;

			if ((contentType != null) && (!bodyHeaders.containsKey(HttpHeaders.CONTENT_TYPE))) {
				bodyHeaders.addHeader(HttpHeaders.CONTENT_TYPE, mediaType.toString());
			}
		}

		@Override
		public void write(int b) {
			baos.write(b);
		}

		@Override
		public void write(byte[] b) {
			baos.write(b, 0, b.length);
		}

		@Override
		public void write(byte[] b, int off, int len) {
			baos.write(b, off, len);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() throws IOException {
			if (!closed) {
				Body body = null;

				try {
					byte[] source = baos.toByteArray();

					body = VordelBodyProvider.readFrom(bodyHeaders, contentType, source);
				} finally {
					if (body == null) {
						message.remove(MessageProperties.CONTENT_BODY);
					} else {
						message.put(MessageProperties.CONTENT_BODY, body);
					}

					closed |= true;
				}
			}
		}
	}
}
