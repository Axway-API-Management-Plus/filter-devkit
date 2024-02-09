package com.vordel.circuit.filter.devkit.script;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.nio.Buffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vordel.circuit.cache.BodySerializer;
import com.vordel.circuit.filter.devkit.context.resources.SelectorResource;
import com.vordel.common.xml.XmlParserCache;
import com.vordel.dwe.ContentSource;
import com.vordel.dwe.InputStreamContentSource;
import com.vordel.el.Selector;
import com.vordel.mime.Body;
import com.vordel.mime.ContentType;
import com.vordel.mime.FormURLEncodedBody;
import com.vordel.mime.HeaderSet;
import com.vordel.mime.JSONBody;
import com.vordel.mime.QueryStringHeaderSet;
import com.vordel.mime.XMLBody;
import com.vordel.trace.Trace;

public class ScriptHelper {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private ScriptHelper() {
	}

	/**
	 * create a selector from the given expression (without juel start/end
	 * delimiters). purpose of this method is to create usable selectors from
	 * scripts. It's equivalent of SelectorExpression parameter injection. this
	 * method is mis-named, use
	 * {@link SelectorResource#fromExpression(String, Class)} instead.
	 * 
	 * @param <T>           expected type
	 * @param attributeName name of attribute in message (can be a selector
	 *                      attribute)
	 * @param clazz         expected type
	 * @return selector for the given attribute.
	 */
	@Deprecated
	public static <T> Selector<T> toAttributeSelector(String attributeName, Class<T> clazz) {
		return SelectorResource.fromExpression(attributeName, clazz);
	}

	public static InputStream toInputStream(Body body) {
		byte[] buffer = toByteArray(body);

		return buffer == null ? null : new ByteArrayInputStream(buffer);
	}

	public static byte[] toByteArray(Body body) {
		byte[] buffer = null;

		if (body != null) {
			try {
				ByteArrayOutputStream bos = new ByteArrayOutputStream();

				body.write(bos, Body.WRITE_NO_CTE);
				buffer = bos.toByteArray();
			} catch (IOException e) {
				Trace.error("Can't convert body to byte array", e);
			}
		}

		return buffer;
	}

	public static String toString(Body body) {
		String value = null;

		try {
			if (body instanceof JSONBody) {
				JsonNode node = ((JSONBody) body).getJSON();

				value = MAPPER.writeValueAsString(node);
			} else if (body instanceof XMLBody) {
				/* use DOM API since requested charset may not be accurate */
				value = transformToString(((XMLBody) body).getDocument());
			} else if (body != null) {
				ContentType contentType = body.getContentType();
				byte[] buffer = toByteArray(body);
				String charset = null;

				if (contentType != null) {
					charset = contentType.get("charset");
				}

				value = new String(buffer, charset == null ? "UTF-8" : charset);
			}
		} catch (IOException e) {
			Trace.error("Can't convert body to string", e);
		}

		return value;
	}

	private static String transformToString(Document element) {
		String value = null;

		try {
			StringWriter output = new StringWriter();
			Source source = new DOMSource(element);
			Result result = new StreamResult(output);

			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();

			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.setOutputProperty(OutputKeys.INDENT, "no");

			transformer.transform(source, result);
		} catch (TransformerException e) {
			Trace.error("Unable to Transform XML", e);
		}

		return value;
	}

	public static JSONBody toJSONBody(JsonNode node) {
		JSONBody body = null;

		if (node != null) {
			HeaderSet headers = new HeaderSet();
			ContentType contentType = new ContentType(ContentType.Authority.MIME, MediaType.APPLICATION_JSON);

			headers.addHeader(HttpHeaders.CONTENT_TYPE, contentType.getType());
			body = new JSONBody(headers, contentType, node);
		}

		return body;
	}

	private static Body toBody(ContentType contentType, InputStream in) {
		HeaderSet headers = new HeaderSet();
		ContentSource source = new InputStreamContentSource(in);

		headers.addHeader(HttpHeaders.CONTENT_TYPE, contentType.getType());

		return Body.create(headers, contentType, source);
	}

	public static JSONBody toJSONBody(InputStream in) {
		JSONBody body = null;

		if (in != null) {
			ContentType contentType = new ContentType(ContentType.Authority.MIME, MediaType.APPLICATION_JSON);

			body = (JSONBody) toBody(contentType, in);
		}

		return body;
	}

	public static JSONBody toJSONBody(String json) {
		JSONBody body = null;

		if (json != null) {
			try {
				body = toJSONBody(MAPPER.readTree(json));
			} catch (IOException e) {
				Trace.error("Can't convert string to JSON", e);
			}
		}

		return body;
	}

	public static XMLBody toXMLBody(Document document) {
		XMLBody body = null;

		if (document != null) {
			HeaderSet headers = new HeaderSet();
			ContentType contentType = new ContentType(ContentType.Authority.MIME, MediaType.APPLICATION_XML);
			String charset = document.getXmlEncoding();

			if (charset != null) {
				contentType.put("charset", charset);
			}

			headers.addHeader(HttpHeaders.CONTENT_TYPE, contentType.getType());
			body = new XMLBody(headers, contentType, document);
		}

		return body;
	}

	public static XMLBody toXMLBody(InputStream in) {
		XMLBody body = null;

		if (in != null) {
			ContentType contentType = new ContentType(ContentType.Authority.MIME, MediaType.APPLICATION_XML);

			body = (XMLBody) toBody(contentType, in);
		}

		return body;
	}

	public static XMLBody toXMLBody(String xml) {
		XMLBody body = null;

		if (xml != null) {
			try {
				InputSource source = XmlParserCache.getSource(xml);
				Document document = XmlParserCache.getDocument(source);

				body = toXMLBody(document);
			} catch (SAXException e) {
				Trace.error("Can't convert string to XML", e);
			} catch (IOException e) {
				Trace.error("Can't convert string to XML", e);
			}
		}

		return body;
	}

	public static FormURLEncodedBody toFormURLEncodedBody(InputStream in) {
		FormURLEncodedBody body = null;

		if (in != null) {
			ContentType contentType = new ContentType(ContentType.Authority.MIME, MediaType.APPLICATION_FORM_URLENCODED);

			body = (FormURLEncodedBody) toBody(contentType, in);
		}

		return body;
	}

	public static FormURLEncodedBody toFormURLEncodedBody(QueryStringHeaderSet parameters) {
		FormURLEncodedBody body = null;

		if (parameters != null) {
			HeaderSet headers = new HeaderSet();
			ContentType contentType = new ContentType(ContentType.Authority.MIME, MediaType.APPLICATION_FORM_URLENCODED);

			headers.addHeader(HttpHeaders.CONTENT_TYPE, contentType.getType());
			body = new FormURLEncodedBody(headers, contentType, parameters);
		}

		return body;
	}

	public static FormURLEncodedBody toFormURLEncodedBody(String form) {
		return toFormURLEncodedBody(form, "UTF-8");
	}

	public static FormURLEncodedBody toFormURLEncodedBody(String form, String encoding) {
		QueryStringHeaderSet parameters = toQueryStringHeaderSet(form, encoding);
		FormURLEncodedBody body = null;

		if (parameters != null) {
			HeaderSet headers = new HeaderSet();
			ContentType contentType = new ContentType(ContentType.Authority.MIME, MediaType.APPLICATION_FORM_URLENCODED);

			headers.addHeader(HttpHeaders.CONTENT_TYPE, contentType.getType());
			body = new FormURLEncodedBody(headers, contentType, parameters);
		}

		return body;
	}

	public static QueryStringHeaderSet toQueryStringHeaderSet(String form) {
		return toQueryStringHeaderSet(form, "UTF-8");
	}

	public static QueryStringHeaderSet toQueryStringHeaderSet(String form, String encoding) {
		QueryStringHeaderSet set = null;

		if (form != null) {
			if ((encoding == null) || encoding.isEmpty()) {
				encoding = "UTF-8";
			}

			try {
				set = (QueryStringHeaderSet) FormURLEncodedBody.parseParams(form, encoding);
			} catch (IOException e) {
				Trace.error("Can't convert string to QueryString", e);
			}
		}

		return set;
	}

	public static String asSerializable(Object value) throws IOException {
		String result = null;

		if (value instanceof Body) {
			value = new BodySerializer((Body) value);
		}

		if ((value == null) || (value instanceof Serializable)) {
			result = encodeSerializable((Serializable) value);
		}

		return result;
	}

	public static Object asObject(String element) throws IOException {
		Object result = null;

		if (element != null) {
			result = decodeSerializable(element);

			if (result instanceof BodySerializer) {
				result = ((BodySerializer) result).getAsBody();
			}
		}

		return result;
	}

	/**
	 * charset used to decode/encode literal strings, it provides a 1 to 1 mapping
	 * of bytes to characters
	 */
	private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
	/**
	 * Special constant to recognize object stream header
	 */
	private static final String STREAM_MAGIC = computeMagic();

	private static String computeMagic() {
		String result = null;

		try {
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			ObjectOutputStream out = new ObjectOutputStream(buffer);

			try {
				out.writeShort(0x89c3);
			} finally {
				/* close object outputstream to ensure output is flushed */
				out.close();
			}

			/* output buffer as a Java string literal */
			result = encodeLiteral(buffer.toByteArray());
		} catch (IOException e) {
			throw new IllegalStateException("can't encode value", e);
		}

		return unescapeLiteral(result);
	}

	private static String unescapeMagic(String data) {
		return unescapeLiteral(data, STREAM_MAGIC.length());
	}

	public static String encodeSerializable(Serializable value) {
		String result = null;

		if ((value instanceof String) && (!unescapeMagic((String) value).startsWith(STREAM_MAGIC))) {
			/* shortcut for strings which do not start with STREAM_MAGIC */
			result = (String) value;
		} else if ((value == null) || (value instanceof Serializable)) {
			try {
				ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				ObjectOutputStream out = new ObjectOutputStream(buffer);

				try {
					/* write magic */
					out.writeShort(0x89c3);

					/* serialize the object */
					out.writeObject(value);
				} finally {
					/* close object outputstream to ensure output is flushed */
					out.close();
				}

				/* output buffer as a Java string literal */
				result = encodeLiteral(buffer.toByteArray());
			} catch (IOException e) {
				throw new IllegalArgumentException("can't encode value", e);
			}
		} else {
			throw new IllegalArgumentException("provided value is not serializable");
		}

		return result;
	}

	public static Serializable decodeSerializable(String data) {
		Serializable result = null;

		if (data != null) {
			/* unescape first characters to check for stream magic */
			data = unescapeMagic(data);

			if (data.startsWith(STREAM_MAGIC)) {
				try {
					ByteArrayInputStream buffer = new ByteArrayInputStream(decodeLiteral(data));
					ObjectInputStream in = new ObjectInputStream(buffer);

					try {
						in.readShort();

						result = (Serializable) in.readObject();
					} finally {
						in.close();
					}
				} catch (ClassNotFoundException e) {
					throw new IllegalArgumentException("can't decode value", e);
				} catch (IOException e) {
					throw new IllegalArgumentException("can't decode value", e);
				}
			} else {
				/* not starting by Java serializer MAGIC */
				result = data;
			}
		}

		return result;
	}

	/**
	 * create a Java Literal constant from arbitrary binary data
	 * 
	 * @param data binary data to be encoded
	 * @return a literal string suitable for insertion in Java source (or logs)
	 */
	public static String encodeLiteral(byte[] data) {
		/* encode data as raw ISO 8859-1 (can encode any kind of binary data) */
		return data == null ? null : encodeLiteral(new String(data, ISO_8859_1));
	}

	/**
	 * create a Java Literal constant from arbitrary string
	 * 
	 * @param data string value to be encoded
	 * @return a literal string suitable for insertion in Java source (or logs)
	 */
	public static String encodeLiteral(String data) {
		String result = null;

		if (data != null) {
			char[] encoded = data.toCharArray();
			int length = 0;

			/* loop a first time against characters to compute literal max length */
			for (char c : encoded) {
				switch (c) {
				case '\b':
				case '\t':
				case '\n':
				case '\f':
				case '\r':
				case '"':
				case '\\':
					length += 2;
					break;
				case '\'':
					/* simple quote does not need escape for java string literal */
				default:
					if (Character.isISOControl(c)) {
						/*
						 * octal escapes use 4 characters versus 6 in unicode escape, this is why we use
						 * it
						 */
						length += 4;
					} else {
						length += 1;
					}
				}
			}

			StringBuilder builder = new StringBuilder(length);

			for (int index = 0; index < encoded.length; index++) {
				char c = encoded[index];

				switch (c) {
				case '\b':
					builder.append("\\b");
					break;
				case '\t':
					builder.append("\\t");
					break;
				case '\n':
					builder.append("\\n");
					break;
				case '\f':
					builder.append("\\f");
					break;
				case '\r':
					builder.append("\\r");
					break;
				case '"':
					builder.append("\\\"");
					break;
				case '\\':
					builder.append("\\\\");
					break;
				case '\'':
					/* simple quote does not need escape for java string literal */
				default:
					if (Character.isISOControl(c)) {
						int next = index + 1;

						/*
						 * octal escapes use at most 4 characters versus 6 in unicode escape, this is
						 * why we use it
						 */
						if ((next == encoded.length) || (!Character.isDigit(encoded[next]))) {
							/* use short escape */
							builder.append(String.format("\\%o", (int) c));
						} else {
							/* use long escape */
							builder.append(String.format("\\%03o", (int) c));
						}
					} else {
						/* regular case... we do not need escape */
						builder.append(c);
					}
				}
			}

			result = builder.toString();
		}

		return result;
	}

	public static String unescapeLiteral(String data) {
		return unescapeLiteral(data, Integer.MAX_VALUE);
	}

	public static byte[] decodeLiteral(String data) {
		byte[] result = null;

		if (data != null) {
			/* convert character array into byte raw data */
			result = unescapeLiteral(data).getBytes(ISO_8859_1);
		}

		return result;
	}

	private static String unescapeLiteral(String data, int length) {
		String result = null;

		if (data != null) {
			CharBuffer encoded = CharBuffer.wrap(data.toCharArray());
			CharBuffer escape = CharBuffer.allocate(4);
			StringBuilder builder = new StringBuilder(encoded.remaining());

			while ((encoded.remaining() > 0) && (builder.length() < length)) {
				char c = encoded.get();

				if (c == '\\') {
					/* encountered an escape sequence... handle it */
					c = handleEscape(encoded, escape);
				}

				builder.append(c);
			}

			if (encoded.remaining() > 0) {
				builder.append(encoded.toString());
			}

			/* convert character array into byte raw data */
			result = builder.toString();
		}

		return result;
	}

	private static char handleEscape(CharBuffer encoded, CharBuffer escape) {
		if (encoded.remaining() < 1) {
			throw new IllegalStateException("illegal escape sequence (buffer underflow)");
		}

		char c = encoded.get();

		switch (c) {
		case 'b':
			c = '\b';
			break;
		case 't':
			c = '\t';
			break;
		case 'n':
			c = '\n';
			break;
		case 'f':
			c = '\f';
			break;
		case 'r':
			c = '\r';
			break;
		case '"':
			c = '"';
			break;
		case '\'':
			c = '\'';
			break;
		case '\\':
			c = '\\';
			break;
		case '0':
		case '1':
		case '2':
		case '3':
			/* we may have a three digit octal escape */
			c = handleOctalEscape(c, encoded, escape, 3);
			break;
		case '4':
		case '5':
		case '6':
		case '7':
			/* we may have a two digit octal escape */
			c = handleOctalEscape(c, encoded, escape, 2);
			break;
		case 'u':
			c = handleUnicodeEscape(encoded, escape);
			break;
		default:
			throw new IllegalStateException("illegal escape sequence");
		}

		return c;
	}

	private static char handleUnicodeEscape(CharBuffer encoded, CharBuffer escape) {
		int remaining = 4;

		((Buffer) escape).clear(); /* Silly workaround for Java 8 >= release 211*/
		
		while ((remaining > 0) && hasHexDigit(encoded)) {
			/* copy escape character */
			escape.put(encoded.get());

			/* decrement remaining length */
			remaining--;
		}

		if (remaining > 0) {
			throw new IllegalStateException("invalid unicode escape sequence");
		}

		return (char) Integer.parseInt(((Buffer) escape).flip().toString().toLowerCase(), 16);
	}

	private static char handleOctalEscape(char c, CharBuffer encoded, CharBuffer escape, int maxLength) {
		((Buffer) escape).clear(); /* Silly workaround for Java 8 >= release 211*/
		escape.put(c);

		/* we put the first character, report it */
		maxLength -= 1;

		while ((maxLength > 0) && hasOctalDigit(encoded)) {
			/* copy escape character */
			escape.put(encoded.get());

			/* decrement remaining length */
			maxLength--;
		}

		return (char) Integer.parseInt(((Buffer) escape).flip().toString(), 8);
	}

	private static boolean hasOctalDigit(CharBuffer encoded) {
		boolean result = false;

		if (encoded.remaining() > 0) {
			int position = encoded.position();
			char c = encoded.get(position);

			switch (c) {
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
				result |= true;
				break;
			default:
				break;
			}
		}

		return result;
	}

	private static boolean hasHexDigit(CharBuffer encoded) {
		boolean result = false;

		if (encoded.remaining() > 0) {
			int position = encoded.position();
			char c = encoded.get(position);

			switch (c) {
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
			case 'a':
			case 'b':
			case 'c':
			case 'd':
			case 'e':
			case 'f':
			case 'A':
			case 'B':
			case 'C':
			case 'D':
			case 'E':
			case 'F':
				result |= true;
				break;
			default:
				break;
			}
		}

		return result;
	}
}
