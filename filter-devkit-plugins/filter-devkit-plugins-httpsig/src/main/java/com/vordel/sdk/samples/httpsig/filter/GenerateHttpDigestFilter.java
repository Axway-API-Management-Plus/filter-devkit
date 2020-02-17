package com.vordel.sdk.samples.httpsig.filter;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.ext.filter.quick.QuickFilterField;
import com.vordel.circuit.ext.filter.quick.QuickFilterType;
import com.vordel.circuit.ext.filter.quick.QuickJavaFilterDefinition;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.es.Entity;
import com.vordel.mime.Body;
import com.vordel.mime.HeaderSet;
import com.vordel.sdk.samples.httpsig.DigestTemplate;
import com.vordel.sdk.samples.httpsig.DigestValue;

@QuickFilterType(name = "HttpDigestFilter", resources = "generate_digest.properties", ui = "generate_digest.xml")
public class GenerateHttpDigestFilter extends QuickJavaFilterDefinition {
	protected static final Selector<Body> BODY_SELECTOR = SelectorResource.fromExpression(MessageProperties.CONTENT_BODY, Body.class);

	private final List<Selector<String>> selectors = new ArrayList<Selector<String>>();;
	private boolean generateContentMD5 = false;

	@QuickFilterField(name = "generateContentMD5", cardinality = "?", type = "integer")
	private void generateContentMD5(ConfigContext ctx, Entity entity, String field) {
		generateContentMD5 = entity.getBooleanValue(field);
	}

	@QuickFilterField(name = "digestAlgorithms", cardinality = "*", type = "string")
	private void setDigestAlgorithms(ConfigContext ctx, Entity entity, String field) {
		Collection<String> values = entity.getStringValues(field);

		if (values != null) {
			for(String value : values) {
				selectors.add(new Selector<String>(value, String.class));
			}
		}
	}

	@Override
	public boolean invokeFilter(Circuit c, Message m) throws CircuitAbortException {
		List<String> resolved = new ArrayList<String>();
		Body body = BODY_SELECTOR.substitute(m);

		for(Selector<String> selector : selectors) {
			String value = selector.substitute(m);

			if (value != null) {
				value = value.trim();

				if ((!value.isEmpty()) && (!resolved.contains(value)))  {
					resolved.add(value);
				}
			}
		}

		boolean result = true;

		if (!resolved.isEmpty()) {
			result &= generateEntityDigest(m, body, resolved.toArray(new String[0]));
		}

		if (result && generateContentMD5) {
			result &= generateContentMD5(m, body);
		}

		return result;
	}

	@Override
	public void attachFilter(ConfigContext ctx, Entity entity) {
		/* nothing more to do here... */
	}

	@Override
	public void detachFilter() {
		generateContentMD5 = false;
		selectors.clear();
	}

	public static boolean generateEntityDigest(Message msg, Body body, String... algorithms) throws CircuitAbortException {
		boolean result = false;

		try {
			HeaderSet headers = HttpMessageParser.getMessageHeaders(msg, false);

			if (headers != null) {
				headers.remove("Digest");
			}

			if (body != null) {
				DigestTemplate<Message> template = new DigestTemplate<Message>(HttpMessageParser.PARSER, algorithms);
				DigestValue<Message> digest = template.digest(msg);

				headers = body.getHeaders();
				headers.remove("Digest");

				for (String value : digest.getDigestValues(new ArrayList<String>())) {
					headers.addHeader("Digest", value);
				}

				/* signal digest success */
				result = true;
			}
		} catch (IllegalArgumentException e) {
			throw new CircuitAbortException("Invalid digest template", e);
		} catch (IOException e) {
			throw new CircuitAbortException("Unable to read message body", e);
		} catch (NoSuchAlgorithmException e) {
			throw new CircuitAbortException("A Digest Algorithm is missing", e);
		}

		return result;
	}

	public static boolean generateContentMD5(Message msg, Body body) throws CircuitAbortException {
		boolean result = false;

		try {
			HeaderSet headers = HttpMessageParser.getMessageHeaders(msg, false);

			if (headers != null) {
				headers.remove("Content-MD5");
			}

			if (body != null) {
				String digest = DigestTemplate.getContentMD5(HttpMessageParser.PARSER, msg);

				headers = body.getHeaders();
				headers.remove("Content-MD5");

				if (digest != null) {
					headers.setHeader("Content-MD5", digest);
				}

				/* signal digest success */
				result = true;
			}
		} catch (IOException e) {
			throw new CircuitAbortException("Unable to read message body", e);
		} catch (NoSuchAlgorithmException e) {
			throw new CircuitAbortException("MD5 is not available", e);
		}

		return result;
	}
}
