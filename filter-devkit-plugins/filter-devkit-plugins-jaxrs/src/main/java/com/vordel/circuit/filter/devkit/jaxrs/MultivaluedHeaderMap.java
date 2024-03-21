package com.vordel.circuit.filter.devkit.jaxrs;

import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import com.vordel.mime.Headers;

public class MultivaluedHeaderMap extends AbstractMultivaluedHeaderMap {
	private final Headers headers;

	public MultivaluedHeaderMap(Headers headers) {
		this.headers = headers;
	}

	@Override
	protected Headers getHeaders() {
		return headers;
	}

	public static <T extends Headers> T toHeaders(T to, MultivaluedMap<String, String> from) {
		if (to != null) {
			Iterator<String> names = from.keySet().iterator();

			while (names.hasNext()) {
				String name = names.next();
				List<String> values = from.get(name);

				if (values != null) {
					for (String value : values) {
						to.addHeader(name, value);
					}
				}
			}
		}

		return to;
	}

	/**
	 * copy headers from a Headers object into another Headers object
	 * 
	 * @param <T> headers type
	 * @param to   header target headers
	 * @param from source headers
	 * @return target headers
	 */
	public static <T extends Headers> T fromHeaders(T to, Headers from) {
		if (to != null) {
			Iterator<String> names = from.getHeaderNames();

			while (names.hasNext()) {
				String name = names.next();
				Iterator<String> values = from.getHeaders(name);

				while (values.hasNext()) {
					to.addHeader(name, values.next());
				}
			}
		}
		return to;
	}

	/**
	 * copy headers from a Headers object into a header map
	 * 
	 * @param <T> header map type
	 * @param to   target header map
	 * @param from source headers
	 * @return target header map
	 */
	public static <T extends MultivaluedMap<String, String>> T fromHeaders(T to, Headers from) {
		if (to != null) {
			Iterator<String> names = from.getHeaderNames();

			while (names.hasNext()) {
				String name = names.next();
				Iterator<String> values = from.getHeaders(name);

				while (values.hasNext()) {
					to.add(name, values.next());
				}
			}
		}

		return to;
	}

	/**
	 * copy headers from a header map object into a header map
	 * 
	 * @param <T> header map type
	 * @param <E> header value type
	 * @param to   target header map
	 * @param from source header map
	 * @return target header map
	 */
	public static <T extends MultivaluedMap<String, E>, E> T mergeHeaders(T to, MultivaluedMap<String, E> from) {
		if (to != null) {
			Iterator<Entry<String, List<E>>> entries = from.entrySet().iterator();

			while (entries.hasNext()) {
				Entry<String, List<E>> entry = entries.next();
				String name = entry.getKey();
				Iterator<E> values = entry.getValue().iterator();

				while (values.hasNext()) {
					to.add(name, values.next());
				}
			}
		}

		return to;
	}

	/**
	 * wraps a Headers object into a JAX-RS header map
	 * 
	 * @param from original headers object
	 * @return JAX-RS header map
	 */
	public static MultivaluedHeaderMap wrapHeaders(Headers from) {
		return new MultivaluedHeaderMap(from);
	}
}
