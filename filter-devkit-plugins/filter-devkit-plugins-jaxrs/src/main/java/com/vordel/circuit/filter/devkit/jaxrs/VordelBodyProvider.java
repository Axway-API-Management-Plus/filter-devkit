package com.vordel.circuit.filter.devkit.jaxrs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map.Entry;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.dwe.ByteArrayContentSource;
import com.vordel.dwe.InputStreamContentSource;
import com.vordel.mime.Body;
import com.vordel.mime.ContentType;
import com.vordel.mime.HeaderSet;
import com.vordel.mime.HeaderSet.Header;
import com.vordel.mime.HeaderSet.HeaderEntry;

@Provider
@Consumes(MediaType.WILDCARD)
@Produces(MediaType.WILDCARD)
public class VordelBodyProvider implements MessageBodyReader<Body>, MessageBodyWriter<Body> {
	private static final VordelBodyProvider INSTANCE = new VordelBodyProvider();

	public static VordelBodyProvider getInstance() {
		return INSTANCE;
	}

	protected VordelBodyProvider() {
	}

	@Override
	public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return Body.class.isAssignableFrom(type);
	}

	@Override
	public Body readFrom(Class<Body> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
		HeaderSet bodyHeaders = MultivaluedHeaderMap.toHeaders(new HeaderSet(), httpHeaders).separateContentHeaders();
		String subType = mediaType.getSubtype();
		ContentType contentType = null;

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

		if (!bodyHeaders.containsKey(HttpHeaders.CONTENT_TYPE)) {
			bodyHeaders.addHeader(HttpHeaders.CONTENT_TYPE, mediaType.toString());
		}

		return readFrom(bodyHeaders, contentType, entityStream);
	}

	@Override
	public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return Body.class.isAssignableFrom(type);
	}

	@Override
	public void writeTo(Body body, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
		if (body != null) {
			HeaderSet headers = body.getHeaders();

			for (Entry<String, HeaderEntry> entry : headers.entrySet()) {
				String headerName = entry.getKey();

				if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(headerName)) {
					/* skip this header it is already handled by JAX-RS runtime */
				} else if (headerName != null) {
					httpHeaders.remove(headerName);

					for (Header header : entry.getValue()) {
						String headerValue = header == null ? null : header.toString();

						httpHeaders.add(headerName, headerValue);
					}
				}
			}

			writeTo(body, entityStream);
		}
	}

	@Deprecated
	public static HeaderSet asHeaderSet(MultivaluedMap<String, String> httpHeaders) {
		return MultivaluedHeaderMap.toHeaders(new HeaderSet(), httpHeaders);
	}

	public static Body readFrom(HeaderSet bodyHeaders, ContentType contentType, InputStream entityStream) {
		Body result = null;

		if ((contentType != null) && (entityStream != null)) {
			InputStreamContentSource source = new InputStreamContentSource(entityStream);

			result = Body.create(bodyHeaders, contentType, source);
		}

		return result;
	}

	public static Body readFrom(HeaderSet bodyHeaders, ContentType contentType, byte[] entity) {
		Body result = null;

		if ((contentType != null) && (entity != null)) {
			ByteArrayContentSource source = new ByteArrayContentSource(entity);

			result = Body.create(bodyHeaders, contentType, source);
		}

		return result;
	}

	public static void writeTo(Body body, OutputStream entityStream) throws IOException {
		if (body != null) {
			body.write(entityStream, Body.WRITE_NO_CTE);
		}
	}

	@Override
	public long getSize(Body t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return -1;
	}

	public static InputStream asInputStream(Body body) throws CircuitAbortException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		if (body != null) {
			try {
				writeTo(body, baos);
			} catch (IOException e) {
				throw new CircuitAbortException("Unable to read Message Body", e);
			}
		}

		return new ByteArrayInputStream(baos.toByteArray());
	}

	public static ContentType asContentType(MediaType mediaType) {
		StringBuilder mimeType = new StringBuilder();

		mimeType.append(mediaType.getType());
		mimeType.append('/');
		mimeType.append(mediaType.getSubtype());

		ContentType contentType = new ContentType(ContentType.Authority.MIME, mimeType.toString());

		for (Entry<String, String> entry : mediaType.getParameters().entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();

			contentType.put(key, value);
		}

		return contentType;
	}

	public static MediaType getMediaType(Body body) {
		MediaType mediaType = null;

		if (body != null) {
			HeaderSet headers = body.getHeaders();
			String mimeType = headers.getHeader(HttpHeaders.CONTENT_TYPE);

			if (mimeType != null) {
				mediaType = MediaType.valueOf(mimeType);
			} else {
				ContentType contentType = body.getContentType();

				mediaType = MediaType.valueOf(contentType.toString());
			}
		}

		return mediaType;
	}
	
	public static ResponseBuilder asResponseBuilder(int status, Body body) {
		ResponseBuilder builder = Response.status(status);
		String mimeType = body.getHeaders().getHeader(HttpHeaders.CONTENT_TYPE);
		
		return builder.entity(body).type(mimeType);
	}
}
