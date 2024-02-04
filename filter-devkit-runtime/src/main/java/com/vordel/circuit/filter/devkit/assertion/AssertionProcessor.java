package com.vordel.circuit.filter.devkit.assertion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.filter.devkit.context.resources.SelectorResource;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.dwe.http.HTTPPlugin;
import com.vordel.el.Selector;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;
import com.vordel.es.EntityType;
import com.vordel.statistics.MessageMetrics;

public class AssertionProcessor extends MessageProcessor {
	private Collection<AssertionProperty> properties = null;

	private Selector<Boolean> assertion = null;
	private Selector<String> reportAs = null;

	private Selector<String> errorMessage = null;
	private Selector<String> errorAttribute = null;

	private Selector<Boolean> useExistingCause;

	private Selector<Boolean> setHttpStatus;
	private Selector<Integer> httpStatus;

	private Selector<Boolean> setResponseStatus;
	private Selector<Integer> responseStatus;

	@Override
	public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
		super.filterAttached(ctx, entity);

		Selector<Boolean> assertion = SelectorResource.fromLiteral(entity.getStringValue("expression"), Boolean.class, true);
		Selector<String> reportAs = SelectorResource.fromLiteral(entity.getStringValue("reportErrorAs"), String.class, true);
		Selector<String> errorAttribute = SelectorResource.fromLiteral(entity.getStringValue("errorAttribute"), String.class, true);
		Selector<String> errorMessage = SelectorResource.fromLiteral(entity.getStringValue("errorMessage"), String.class, true);

		Selector<Boolean> useExistingCause = SelectorResource.fromLiteral(entity.getStringValue("useExistingCause"), Boolean.class, true);
		Selector<Boolean> setHttpStatus = SelectorResource.fromLiteral(entity.getStringValue("setHttpStatus"), Boolean.class, true);
		Selector<Boolean> setResponseStatus = SelectorResource.fromLiteral(entity.getStringValue("setResponseStatus"), Boolean.class, true);
		Selector<Integer> httpStatus = SelectorResource.fromLiteral(entity.getStringValue("httpStatus"), Integer.class, true);
		Selector<Integer> responseStatus = SelectorResource.fromLiteral(entity.getStringValue("responseStatus"), Integer.class, true);

		EntityStore es = ctx.getStore();

		EntityType propertyType = es.getTypeForName("Property");
		Collection<ESPK> properties = es.listChildren(entity.getPK(), propertyType);

		if (properties == null) {
			properties = Collections.emptySet();
		}

		this.assertion = assertion;
		this.properties = properties(es, properties);
		this.errorMessage = errorMessage;
		this.errorAttribute = errorAttribute;
		this.reportAs = reportAs;
		this.useExistingCause = useExistingCause;
		this.setHttpStatus = setHttpStatus;
		this.setResponseStatus = setResponseStatus;
		this.httpStatus = httpStatus;
		this.responseStatus = responseStatus;
	}

	private static List<AssertionProperty> properties(EntityStore es, Collection<ESPK> properties) {
		List<AssertionProperty> result = new ArrayList<AssertionProperty>();

		for (ESPK element : properties) {
			Entity entity = es.getEntity(element);

			String key = entity.getStringValue("name");
			String value = entity.getStringValue("value");

			if ((key != null) && (value != null)) {
				Selector<String> keyExpression = SelectorResource.fromLiteral(key, String.class, true);
				Selector<Object> valueExpression = SelectorResource.fromLiteral(value, Object.class, false);

				if ((keyExpression != null) && (valueExpression != null)) {
					AssertionProperty property = new AssertionProperty(keyExpression, valueExpression);

					result.add(property);
				}
			}
		}

		if (result.isEmpty()) {
			result = Collections.emptyList();
		}

		return result;
	}

	@Override
	public void filterDetached() {
		super.filterDetached();

		this.properties = null;
		this.assertion = null;
		this.reportAs = null;
		this.errorMessage = null;
		this.errorAttribute = null;
		this.setHttpStatus = null;
		this.setResponseStatus = null;
		this.httpStatus = null;
		this.responseStatus = null;
	}

	@Override
	public boolean invoke(Circuit circuit, Message message) throws CircuitAbortException {
		Boolean result = assertion.substitute(message);
		String type = reportAs.substitute(message);

		if ((result == null) || (!result.booleanValue())) {
			try {
				String reason = null;

				if (errorMessage != null) {
					reason = errorMessage.substitute(message);
				}

				if (reason == null) {
					reason = "Assertion Failure";
				}

				if ((setHttpStatus != null) && (setHttpStatus.substitute(message))) {
					Integer status = httpStatus.substitute(message);

					if (status != null) {
						String info = HTTPPlugin.getResponseText(status);

						message.put(MessageProperties.HTTP_RSP_STATUS, status);
						message.put(MessageProperties.HTTP_RSP_INFO, info);
					}
				}

				if ((setResponseStatus != null) && setResponseStatus.substitute(message)) {
					Integer status = responseStatus.substitute(message);

					if (status != null) {
						message.put("user.set.response.status", true);

						MessageMetrics.setResponseStatus(message, status, getCategory());
					}
				}

				if (properties != null) {
					for (AssertionProperty property : properties) {
						property.setValue(message);
					}
				}

				if ("REPORTAS_FAULT".equals(type)) {
					Object cause = message.get(MessageProperties.CIRCUIT_EXCEPTION);
					if ((cause instanceof Throwable) && (useExistingCause != null) && useExistingCause.substitute(message)) {
						throw new CircuitAbortException(reason, (Throwable) cause);
					} else {
						throw new CircuitAbortException(reason);
					}
				} else if ("REPORTAS_FAILURE".equals(type)) {
					String attributeName = errorAttribute.substitute(message);

					if (attributeName != null) {
						message.put(attributeName, reason);
					}
				}
			} finally {
				result = Boolean.FALSE;
			}
		}

		return result;
	}

	private static class AssertionProperty {
		private final Selector<String> key;
		private final Selector<Object> value;

		private AssertionProperty(Selector<String> key, Selector<Object> value) {
			this.key = key;
			this.value = value;
		}

		private void setValue(Message message) {
			String key = this.key.substitute(message);
			Object value = this.value.substitute(message);

			if (key != null) {
				message.put(key, value);
			}
		}
	}
}
