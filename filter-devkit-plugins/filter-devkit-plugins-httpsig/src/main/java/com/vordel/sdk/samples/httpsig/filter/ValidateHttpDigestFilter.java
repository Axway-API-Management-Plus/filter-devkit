package com.vordel.sdk.samples.httpsig.filter;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.ext.filter.quick.QuickFilterField;
import com.vordel.circuit.ext.filter.quick.QuickFilterType;
import com.vordel.circuit.ext.filter.quick.QuickJavaFilterDefinition;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.es.Entity;
import com.vordel.sdk.samples.httpsig.DigestAlgorithm;
import com.vordel.sdk.samples.httpsig.DigestValue;
import com.vordel.trace.Trace;

@QuickFilterType(name = "ValidateHttpDigestFilter", resources = "validate_digest.properties", ui = "validate_digest.xml")
public class ValidateHttpDigestFilter extends QuickJavaFilterDefinition {
	private final List<Selector<String>> requiredAlgorithms = new ArrayList<Selector<String>>();;
	private Selector<Boolean> validateContentMD5 = null;

	@QuickFilterField(name = "validateContentMD5", cardinality = "?", type = "integer")
	private void generateContentMD5(ConfigContext ctx, Entity entity, String field) {
		validateContentMD5 = SelectorResource.fromLiteral(entity.getStringValue(field), Boolean.class, true);
	}

	@QuickFilterField(name = "digestAlgorithms", cardinality = "*", type = "string")
	private void setDigestAlgorithms(ConfigContext ctx, Entity entity, String field) {
		Collection<String> values = entity.getStringValues(field);

		requiredAlgorithms.clear();

		if (values != null) {
			for (String value : values) {
				Selector<String> selector = SelectorResource.fromLiteral(value, String.class, true);

				if (selector != null) {
					requiredAlgorithms.add(selector);
				}
			}
		}
	}

	@Override
	public boolean invokeFilter(Circuit c, Message m) throws CircuitAbortException {
		/*
		 * start by validating the Content-MD5 header, the 'validateContentMD5'
		 * indicated if this header is mandatory. Any invalid hash in headers (even if
		 * not requested) will trigger an error.
		 */
		boolean result = validateContentMD5(m, validateContentMD5 == null ? false : validateContentMD5.substitute(m));

		if (result) {
			/* validate digest header and record available algorithms */
			Set<DigestAlgorithm> algorithms = new HashSet<DigestAlgorithm>();

			if (result &= validateEntityDigest(m, algorithms, false)) {
				/* if validation was successfull, resolve requested algorithms */
				Set<DigestAlgorithm> required = new HashSet<DigestAlgorithm>();

				for (Selector<String> selector : requiredAlgorithms) {
					String value = selector.substitute(m);

					if (value != null) {
						value = value.trim();

						if (!value.isEmpty()) {
							try {
								DigestAlgorithm.get(value);
							} catch (NoSuchAlgorithmException e) {
								throw new CircuitAbortException(String.format("'%s' is not a supported algorithm", e));
							}
						}
					}
				}

				/*
				 * Check if all required algorithms was used in the headers
				 */
				if (!algorithms.containsAll(required)) {
					Trace.error("Digest header is valid but it does not contains all requird algorithms");

					result = false;
				}
			} else {
				Trace.error("Digest header contains invalid entries");
			}
		} else {
			Trace.error("could not validate Content-MD5");
		}

		return result;
	}

	@Override
	public void attachFilter(ConfigContext ctx, Entity entity) {
		/* nothing more to do here... */
	}

	@Override
	public void detachFilter() {
		validateContentMD5 = null;
		requiredAlgorithms.clear();
	}

	public static boolean validateContentMD5(Message msg, boolean failIfMissing) {
		boolean result = false;

		try {
			List<String> digests = HttpMessageParser.PARSER.getHeaderValues(msg, "Content-MD5");

			if ((digests != null) && (!digests.isEmpty())) {
				result = DigestValue.verifyContentMD5(HttpMessageParser.PARSER, msg, HttpMessageParser.PARSER, msg);
			} else {
				Trace.info("no Content-MD5 header found");

				result = !failIfMissing;
			}
		} catch (IOException e) {
			Trace.debug("Unable to read message body", e);
		}

		return result;
	}

	public static boolean validateEntityDigest(Message msg, Set<DigestAlgorithm> algorithms, boolean failIfMissing) {
		boolean result = false;

		try {
			DigestValue<Message> digest = DigestValue.parseDigestHeader(HttpMessageParser.PARSER, HttpMessageParser.PARSER, msg);

			if (digest != null) {
				result = digest.verify(msg);

				for (DigestAlgorithm algorithm : digest.getAlgorithms()) {
					algorithms.add(algorithm);
				}
			} else {
				Trace.info("no Digest header found");

				result = !failIfMissing;
			}
		} catch (IllegalArgumentException e) {
			if (Trace.isDebugEnabled()) {
				Trace.error("Unable to parse digest header", e);
			} else {
				Trace.error("Unable to parse digest header");
			}
		} catch (IOException e) {
			Trace.debug("Unable to read message body", e);
		} catch (NoSuchAlgorithmException e) {
			if (Trace.isDebugEnabled()) {
				Trace.error("A Digest Algorithm is missing", e);
			} else {
				Trace.error("A Digest Algorithm is missing");
			}
		}

		return result;
	}
}
