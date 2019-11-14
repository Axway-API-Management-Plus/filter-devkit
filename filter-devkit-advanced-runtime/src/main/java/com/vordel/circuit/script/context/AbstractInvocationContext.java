package com.vordel.circuit.script.context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.attribute.RetrievedAttribute;
import com.vordel.circuit.script.context.resources.AbstractContextResourceProvider;
import com.vordel.circuit.script.context.resources.CacheResource;
import com.vordel.circuit.script.context.resources.ContextResource;
import com.vordel.circuit.script.context.resources.InvocableResource;
import com.vordel.circuit.script.context.resources.KPSCacheResource;
import com.vordel.circuit.script.context.resources.KPSResource;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.circuit.script.context.resources.SubstitutableResource;
import com.vordel.common.Dictionary;
import com.vordel.config.Circuit;
import com.vordel.dwe.http.HTTPPlugin;
import com.vordel.el.Selector;
import com.vordel.trace.Trace;

public abstract class AbstractInvocationContext extends AbstractContextResourceProvider implements ScriptInvocationContext {
	@Override
	public boolean getPreviousStatus(Message m, int index) throws CircuitAbortException {
		MessageContextTracker tracker = MessageContextTracker.getMessageContextTracker(m);

		if (tracker == null) {
			throw new CircuitAbortException("no context tracker available");
		}

		return tracker.getPreviousStatus(index);
	}

	@Override
	public Object getMessageAttribute(Message m, String name) {
		Object result = m == null ? null : m.get(name);

		return result;
	}

	@Override
	public Object removeMessageAttribute(Message m, String name) {
		Object result = m == null ? null : m.remove(name);

		return result;
	}

	@Override
	public Object setMessageAttribute(Message m, String name, Object value) {
		if (m == null) {
			throw new UnsupportedOperationException();
		}

		return m.put(name, value);
	}

	private static <T> T evaluateExpression(Dictionary dict, String expression, Class<T> clazz) {
		Selector<T> selector = SelectorResource.fromLiteral(expression, clazz, false);

		return selector == null ? null : selector.substitute(dict == null ? Dictionary.empty : dict);
	}

	@Override
	public List<String> getUserAttribute(Message m, String name, String namespace) {
		Map<?, ?> attributes = (Map<?, ?>) getMessageAttribute(m, "attribute.lookup.list");
		List<String> result = null;

		if (attributes != null) {
			if ((namespace != null) || (!"".equals(namespace))) {
				name = String.format("%s:%s", namespace, name);
			}

			RetrievedAttribute attribute = (RetrievedAttribute) attributes.get(name);

			if (attribute != null) {
				result = attribute.getValues();
			}
		}

		return result;
	}

	@Override
	public void removeUserAttribute(Message m, String name, String namespace) {
		Map<?, ?> attributes = (Map<?, ?>) getMessageAttribute(m, "attribute.lookup.list");

		if (attributes != null) {
			if ((namespace != null) || (!"".equals(namespace))) {
				name = String.format("%s:%s", namespace, name);
			}

			attributes.remove(name);
		}
	}

	@Override
	public void addUserAttribute(Message m, String name, String namespace, String value) {
		@SuppressWarnings("unchecked")
		Map<String, RetrievedAttribute> attributes = (Map<String, RetrievedAttribute>) getMessageAttribute(m, "attribute.lookup.list");

		if (attributes == null) {
			attributes = new HashMap<String, RetrievedAttribute>();

			setMessageAttribute(m, "attribute.lookup.list", value);
		}

		ArrayList<String> values = new ArrayList<String>();
		RetrievedAttribute attribute = new RetrievedAttribute(RetrievedAttribute.MSG_ATTR_TYPE, name, null, values, false);
		RetrievedAttribute existing = attributes.get(attribute.getKey());

		values.add(value);

		if (existing != null) {
			existing.getValues().addAll(values);
		} else {
			attributes.put(attribute.getKey(), attribute);
		}

		if ((namespace != null) || (!"".equals(namespace))) {
			attribute = new RetrievedAttribute(RetrievedAttribute.MSG_ATTR_TYPE, name, namespace, values, true);
			existing = attributes.get(attribute.getKey());

			if (existing != null) {
				existing.getValues().addAll(values);
			} else {
				attributes.put(attribute.getKey(), attribute);
			}
		}
	}

	@Override
	public void setUserAttribute(Message m, String name, String namespace, String value) {
		@SuppressWarnings("unchecked")
		Map<String, RetrievedAttribute> attributes = (Map<String, RetrievedAttribute>) getMessageAttribute(m, "attribute.lookup.list");

		if (attributes == null) {
			attributes = new HashMap<String, RetrievedAttribute>();

			setMessageAttribute(m, "attribute.lookup.list", value);
		}

		ArrayList<String> values = new ArrayList<String>();
		RetrievedAttribute attribute = new RetrievedAttribute(RetrievedAttribute.MSG_ATTR_TYPE, name, null, values, false);
		RetrievedAttribute existing = attributes.get(attribute.getKey());

		values.add(value);

		if (existing != null) {
			existing.getValues().clear();

			existing.getValues().addAll(values);
		} else {
			attributes.put(attribute.getKey(), attribute);
		}

		if ((namespace != null) || (!"".equals(namespace))) {
			attribute = new RetrievedAttribute(RetrievedAttribute.MSG_ATTR_TYPE, name, namespace, values, true);
			existing = attributes.get(attribute.getKey());

			if (existing != null) {
				existing.getValues().clear();

				existing.getValues().addAll(values);
			} else {
				attributes.put(attribute.getKey(), attribute);
			}
		}
	}

	@Override
	@Deprecated
	public Object evaluateExpression(Dictionary m, String expression) {
		return evaluateExpression(m, expression, Object.class);
	}

	@Override
	public boolean invoke(Circuit c, Message m, String name) throws CircuitAbortException {
		InvocableResource resource = getInvocableResource(m, name);

		if (resource == null) {
			throw new CircuitAbortException("resource is not invocable or does not exists");
		}

		if (c == null) {
			throw new CircuitAbortException("no policy context");
		}

		if (m == null) {
			throw new CircuitAbortException("no message context");
		}

		return resource.invoke(c, m);
	}

	@Override
	public Object substitute(Dictionary dict, String name) {
		SubstitutableResource<?> resource = getSubstitutableResource(dict, name);
		Object result = null;

		if (resource != null) {
			if (dict == null) {
				dict = Dictionary.empty;
			}

			result = resource.substitute(dict);
		}

		return result;
	}

	@Override
	public CacheResource getCacheResource(Dictionary dict, String name) {
		ContextResource resource = getContextResource(dict, name);

		if (resource instanceof KPSResource) {
			resource = new KPSCacheResource(((KPSResource) resource));
		}

		return resource instanceof CacheResource ? (CacheResource) resource : null;
	}

	@Override
	public void setHttpStatus(Message m, int status) {

		if (m == null) {
			Trace.error("no message context");
		} else {
			String info = HTTPPlugin.getResponseText(status);

			m.put(MessageProperties.HTTP_RSP_STATUS, status);
			m.put(MessageProperties.HTTP_RSP_INFO, info);
		}
	}
}
