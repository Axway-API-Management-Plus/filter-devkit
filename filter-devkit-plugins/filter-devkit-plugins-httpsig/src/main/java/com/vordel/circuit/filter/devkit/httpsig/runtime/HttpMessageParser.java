package com.vordel.circuit.filter.devkit.httpsig.runtime;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.filter.devkit.httpsig.EntityParser;
import com.vordel.circuit.filter.devkit.httpsig.HeaderParser;
import com.vordel.mime.Body;
import com.vordel.mime.HeaderSet;

public class HttpMessageParser implements EntityParser<Message>, HeaderParser<Message> {
	public static final HttpMessageParser PARSER = new HttpMessageParser();

	@Override
	public List<String> getHeaderValues(Message msg, String name) {
		List<String> list = new ArrayList<String>();
		Object body = msg.get(MessageProperties.CONTENT_BODY);

		getValues(list, getMessageHeaders(msg, false), name);

		if (body instanceof Body) {
			getValues(list, (Body) body, name);
		}

		return list.isEmpty() ? null : list;
	}

	private List<String> getValues(List<String> list, Body body, String name) {
		return getValues(list, body == null ? null : body.getHeaders(), name);
	}

	private List<String> getValues(List<String> list, HeaderSet headers, String name) {
		if (headers != null) {
			for (Entry<String, HeaderSet.HeaderEntry> entry : headers.entrySet()) {
				if (name.equalsIgnoreCase(entry.getKey())) {
					for (HeaderSet.Header header : entry.getValue()) {
						if (header != null) {
							list.add(header.toString());
						}
					}
				}
			}
		}

		return list;
	}

	@Override
	public boolean writeEntity(Message entity, OutputStream out) throws IOException {
		boolean written = false;

		if (entity != null) {
			Object body = entity.get(MessageProperties.CONTENT_BODY);

			if (body instanceof Body) {
				((Body) body).write(out, Body.WRITE_NO_CTE);
				
				written = true;
			}
		}

		return written;
	}

	public static void removeHeader(Body body, String name) {
		removeHeader(body == null ? null : body.getHeaders(), name);
	}

	public static void removeHeader(HeaderSet headers, String name) {
		if (headers != null) {
			Set<String> remove = new HashSet<String>();

			for (Entry<String, ?> entry : headers.entrySet()) {
				if (name.equalsIgnoreCase(entry.getKey())) {
					remove.add(name);
				}
			}

			for (String entry : remove) {
				headers.remove(entry);
			}
		}
	}

	public static HeaderSet getMessageHeaders(Message msg, boolean create) {
		Object value = msg.get(MessageProperties.HTTP_HEADERS);
		HeaderSet headers = value instanceof HeaderSet ? (HeaderSet) value : null;

		if (create && (headers == null)) {
			headers = new HeaderSet();

			msg.put(MessageProperties.HTTP_HEADERS, headers);
		}

		return headers;
	}
}