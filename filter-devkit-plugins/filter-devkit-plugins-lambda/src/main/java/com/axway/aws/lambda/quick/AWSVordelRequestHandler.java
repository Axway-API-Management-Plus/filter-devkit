package com.axway.aws.lambda.quick;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.Header;
import org.apache.http.client.methods.HttpRequestBase;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.HandlerContextKey;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.http.HttpResponse;
import com.amazonaws.util.AWSRequestMetrics;
import com.amazonaws.util.TimingInfo;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.dwe.ByteArrayContentSource;
import com.vordel.dwe.HTTPType;
import com.vordel.dwe.Leg;
import com.vordel.dwe.OPEventType;
import com.vordel.dwe.TXNEventType;
import com.vordel.dwe.http.HTTPPlugin;
import com.vordel.mime.Body;
import com.vordel.mime.ContentType;
import com.vordel.mime.HeaderSet;
import com.vordel.trace.Trace;
import com.vordel.vary.IntegerField;
import com.vordel.vary.StringField;
import com.vordel.vary.VariantField;
import com.vordel.vary.VariantType;

public class AWSVordelRequestHandler extends RequestHandler2 {
	public static final HandlerContextKey<Message> VORDEL_MESSAGE = new HandlerContextKey<Message>("VordelMessage");
	public static final HandlerContextKey<String> CONTENT_TYPE = new HandlerContextKey<String>("VordelContentType");
	public static final HandlerContextKey<byte[]> SENT_PAYLOAD = new HandlerContextKey<byte[]>("VordelSentPayload");
	public static final HandlerContextKey<Long> FILTER_START = new HandlerContextKey<Long>("FilterStart");

	private final static Charset ISO8859 = Charset.forName("ISO8859-1");

	private static final IntegerField bytesSent = getVariantField(TXNEventType.instance(), "bytesSent", IntegerField.class);
	private static final IntegerField bytesReceived = getVariantField(TXNEventType.instance(), "bytesReceived", IntegerField.class);
	private static final StringField remoteName = getVariantField(TXNEventType.instance(), "remoteName", StringField.class);
	private static final StringField remoteAddr = getVariantField(TXNEventType.instance(), "remoteAddr", StringField.class);
	private static final StringField remotePort = getVariantField(TXNEventType.instance(), "remotePort", StringField.class);
	private static final IntegerField timestamp = getVariantField(OPEventType.instance(), "timestamp", IntegerField.class);
	private static final IntegerField duration = getVariantField(OPEventType.instance(), "duration", IntegerField.class);

	/*
	 * XXX most of the fields below do not resolve anything... juste in case future
	 * product evolution make them available
	 */

	private static final StringField uri = getVariantField(HTTPType.instance(), "uri", StringField.class);
	private static final IntegerField status = getVariantField(HTTPType.instance(), "status", IntegerField.class);
	private static final StringField statustext = getVariantField(HTTPType.instance(), "statusText", StringField.class);
	private static final StringField method = getVariantField(HTTPType.instance(), "method", StringField.class);
	private static final StringField vhost = getVariantField(HTTPType.instance(), "vhost", StringField.class);

	private static <T extends VariantField> T getVariantField(VariantType type, String name, Class<T> kind) {
		T value = null;

		try {
			/* retrieve field the reflective way */
			Class<? extends VariantType> clazz = type.getClass();
			Field field = clazz.getField(name);

			value = kind.cast(field.get(type));
		} catch (Exception e) {
		}

		return value;
	}

	private static void setVariantField(Leg leg, IntegerField field, long value) {
		if (field != null) {
			field.set(leg.vo, value);
		}
	}

	private static void setVariantField(Leg leg, StringField field, String value) {
		if (field != null) {
			field.set(leg.vo, value);
		}
	}

	@Override
	public HttpResponse beforeUnmarshalling(Request<?> request, HttpResponse received) {
		InputStream in = received.getContent();

		if (in != null) {
			try {
				/* replace content with an input stream which support reset */
				ByteArrayOutputStream content = new ByteArrayOutputStream();
				byte[] buffer = new byte[1024];

				for (int read = in.read(buffer); read > 0; read = in.read(buffer)) {
					content.write(buffer, 0, read);
				}

				in.close();
				received.setContent(in = new ByteArrayInputStream(content.toByteArray()));
			} catch (IOException e) {
				throw new IllegalStateException();
			}
		}

		return received;
	}

	@Override
	public void afterResponse(Request<?> request, Response<?> response) {
		appendLeg(request, response, null);
	}

	@Override
	public void afterError(Request<?> request, Response<?> response, Exception e) {
		appendLeg(request, response, e);
	}

	private void appendLeg(Request<?> request, Response<?> response, Exception err) {
		if (request != null) {
			AmazonWebServiceRequest original = request.getOriginalRequest();

			if (original != null) {
				Message m = original.getHandlerContext(VORDEL_MESSAGE);

				if (m != null) {
					Leg leg = m.correlationId.newLeg(HTTPType.instance(), true);

					try {
						byte[] sent = getSentData(leg, request, response);
						byte[] received = getReceivedData(m, leg, response, err);
						Long start = original.getHandlerContext(FILTER_START);

						m.correlationId.eventLog(leg, "sent", sent, 0, sent.length);

						if (received != null) {
							m.correlationId.eventLog(leg, "received", received, 0, received.length);

							setVariantField(leg, bytesReceived, received.length);
						}

						if (start != null) {
							setVariantField(leg, timestamp, start);
						}

						AWSRequestMetrics metrics = request.getAWSRequestMetrics();
						TimingInfo timing = metrics == null ? null : metrics.getTimingInfo();
						Double elapsed = timing == null ? null : timing.getTimeTakenMillisIfKnown();

						if (elapsed != null) {
							/* retrieve duration from amazon SDK */
							setVariantField(leg, duration, elapsed.longValue());
						} else if (start != null) {
							/* ask for local time to compute duration */
							setVariantField(leg, duration, System.currentTimeMillis() - start);
						}

						setVariantField(leg, bytesSent, sent.length);
					} catch (IOException e) {
						throw new IllegalStateException(e);
					} finally {
						m.correlationId.closeLeg(leg);
					}
				}
			}
		}
	}

	private static HttpResponse getHttpResponse(Response<?> response) {
		return response == null ? null : response.getHttpResponse();
	}

	private static HttpRequestBase getHttpRequest(HttpResponse response) {
		return response == null ? null : response.getHttpRequest();
	}

	private static byte[] getSentData(Leg leg, Request<?> request, Response<?> response) throws IOException {
		AmazonWebServiceRequest original = request.getOriginalRequest();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		HttpRequestBase sent = getHttpRequest(getHttpResponse(response));
		String virtualHost = null;
		byte[] content = null;

		try {
			InputStream in = request.getContent();

			if (in != null) {
				in.reset();

				content = toByteArray(in);
			}
		} catch (IOException e) {
			/* ignore this, take the original provided buffer */
		}	

		if ((content == null) || (content.length == 0)) {
			Trace.debug("aws request payload is null or empty");

			if (original != null) {
				content = original.getHandlerContext(SENT_PAYLOAD);

				if ((content != null) && (content.length > 0)) {
					Trace.debug(String.format("write %d leg bytes from initial message body", content.length));
				}
			}
		} else {
			Trace.debug(String.format("write %d leg bytes from aws request content", content.length));
		}

		if (sent == null) {
			/* write request line */
			out.write(String.format("%s %s %s\r\n", request.getHttpMethod(), request.getResourcePath(), "HTTP/1.1").getBytes(ISO8859));

			for (Entry<String, String> pair : request.getHeaders().entrySet()) {
				String name = pair.getKey();
				String value = pair.getValue();
				boolean skip = false;
				
				skip |= "Content-Length".equalsIgnoreCase(name);
				skip |= "Transfer-Encoding".equalsIgnoreCase(name);
				
				if ("Content-Type".equalsIgnoreCase(name) && ((value == null) || value.trim().isEmpty())) {
					value = original.getHandlerContext(CONTENT_TYPE);

					skip |= (value == null) || value.trim().isEmpty();
				}
				
				if (!skip) {
					out.write(String.format("%s: %s\r\n", name, value).getBytes(ISO8859));

					if ("Host".equalsIgnoreCase(name)) {
						virtualHost = value;
					}
				}
			}
		} else {
			/* write request line */
			out.write(sent.getRequestLine().toString().getBytes(ISO8859));
			out.write("\r\n".getBytes(ISO8859));

			for (Header header : sent.getAllHeaders()) {
				String name = header.getName();
				String value = header.getValue();
				boolean skip = false;

				skip |= "Content-Length".equalsIgnoreCase(name);
				skip |= "Transfer-Encoding".equalsIgnoreCase(name);
				
				if ("Content-Type".equalsIgnoreCase(name) && ((value == null) || value.trim().isEmpty())) {
					value = original.getHandlerContext(CONTENT_TYPE);

					skip |= (value == null) || value.trim().isEmpty();
				}
				
				if (!skip) {
					out.write(String.format("%s: %s\r\n", name, value).getBytes(ISO8859));

					if ("Host".equalsIgnoreCase(name)) {
						virtualHost = value;
					}
				}
			}
		}

		if ((content != null) && (content.length > 0)) {
			out.write(String.format("%s: %d\r\n", "Content-Length", content.length).getBytes(ISO8859));
		}
		
		/* write an empty line before body */
		out.write("\r\n".getBytes(ISO8859));

		setVariantField(leg, method, request.getHttpMethod().toString());
		setVariantField(leg, uri, request.getResourcePath());

		if (virtualHost != null) {
			setVariantField(leg, vhost, virtualHost);
		}

		URI uri = request.getEndpoint();

		if (uri != null) {
			String host = uri.getHost();
			int port = uri.getPort();

			if (port == -1) {
				String scheme = uri.getScheme();

				if ("https".equals(scheme) || "wss".equals(scheme)) {
					port = 443;
				} else if ("http".equals(scheme) || "ws".equals(scheme)) {
					port = 80;
				}
			}

			setVariantField(leg, remoteName, host);

			if (host != null) {
				InetAddress address = InetAddress.getByName(host);

				if (address != null) {
					setVariantField(leg, remoteAddr, address.getHostAddress());
				}
			}

			if (port != -1) {
				setVariantField(leg, remotePort, String.valueOf(port));
			}
		}

		if (content != null) {
			out.write(content);
		} else {
			Trace.debug("aws request payload and initial message body are null or empty");
		}

		return out.toByteArray();
	}

	private byte[] getReceivedData(Message m, Leg leg, Response<?> response, Exception e) throws IOException {
		HttpResponse received = getHttpResponse(response);
		HttpRequestBase sent = getHttpRequest(received);
		byte[] out = null;

		if (received != null) {
			InputStream in = received.getContent();

			in.reset();
			out = toMessage(m, leg, sent.getProtocolVersion().toString(), received.getStatusCode(), received.getStatusText(), received.getHeaders(), in);
		} else if (e instanceof AmazonServiceException) {
			int status = ((AmazonServiceException) e).getStatusCode();
			byte[] in = ((AmazonServiceException) e).getRawResponse();

			out = toMessage(m, leg, "HTTP/1.1", status, null, ((AmazonServiceException) e).getHttpHeaders(), in == null ? null : new ByteArrayInputStream(in));
		}

		return out;
	}

	private static byte[] toMessage(Message m, Leg leg, String protocol, int statusCode, String statusText, Map<String, String> pairs, InputStream in) throws IOException {
		ByteArrayOutputStream log = new ByteArrayOutputStream();

		HeaderSet contentHeaders = null;
		HeaderSet headers = null;

		if (statusText == null) {
			statusText = HTTPPlugin.getResponseText(statusCode);
		}

		setVariantField(leg, status, statusCode);
		setVariantField(leg, statustext, statusText);

		m.put(MessageProperties.HTTP_RSP_STATUS, statusCode);
		m.put(MessageProperties.HTTP_RSP_INFO, statusText);

		/* write status line */
		log.write(String.format("%s %d %s\r\n", protocol, statusCode, statusText).getBytes(ISO8859));

		if (pairs != null) {
			headers = new HeaderSet();

			for (Entry<String, String> pair : pairs.entrySet()) {
				String name = pair.getKey();
				String value = pair.getValue();
				log.write(String.format("%s: %s\r\n", name, value).getBytes(ISO8859));

				headers.addHeader(name, value);
			}

			contentHeaders = headers.separateContentHeaders();
			m.put(MessageProperties.HTTP_HEADERS, headers);
		}

		/* write an empty line before body */
		log.write("\r\n".getBytes(ISO8859));

		byte[] content = toByteArray(in);

		log.write(content);

		if (contentHeaders != null) {
			String media = contentHeaders.getHeader("Content-Type");

			if (media != null) {
				ByteArrayContentSource source = new ByteArrayContentSource(content);
				ContentType contentType = new ContentType(ContentType.Authority.MIME, media);

				m.put(MessageProperties.CONTENT_BODY, Body.create(contentHeaders, contentType, source));
			}
		}

		return log.toByteArray();
	}

	public static byte[] toByteArray(InputStream in) {
		if (in != null) {
			try {
				ByteArrayOutputStream content = new ByteArrayOutputStream();
				byte[] buffer = new byte[1024];

				for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
					content.write(buffer, 0, read);
				}

				return content.toByteArray();
			} catch (IOException e) {
			}
		}

		return null;
	}
}

