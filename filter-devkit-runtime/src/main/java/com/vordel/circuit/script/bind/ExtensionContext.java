package com.vordel.circuit.script.bind;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.script.ScriptHelper;
import com.vordel.circuit.script.context.MessageContextTracker;
import com.vordel.circuit.script.context.resources.AbstractContextResourceProvider;
import com.vordel.circuit.script.context.resources.ContextResource;
import com.vordel.circuit.script.context.resources.InvocableResource;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.circuit.script.context.resources.SubstitutableResource;
import com.vordel.common.Dictionary;
import com.vordel.config.Circuit;
import com.vordel.el.Selector;
import com.vordel.trace.Trace;

import groovy.lang.Script;

public final class ExtensionContext extends AbstractContextResourceProvider {
	private final Map<String, ContextResource> resources;
	private final Class<?> clazz;

	private ExtensionContext(Class<?> clazz, Map<String, ContextResource> resources) {
		this.resources = resources;
		this.clazz = clazz;
	}

	static <T> ExtensionContext create(T script, Class<? extends T> clazz) {
		Map<String, ContextResource> resources = new HashMap<String, ContextResource>();

		if (clazz != null) {
			Map<Method, InvocableMethod> invocables = new HashMap<Method, InvocableMethod>();
			Map<Method, SubstitutableMethod> substitutables = new HashMap<Method, SubstitutableMethod>();
			Set<Method> methods = new HashSet<Method>();

			Set<String> exported = new HashSet<String>();
			Set<String> duplicates = new HashSet<String>();

			for (Method method : scanMethods(clazz, new ArrayList<Method>())) {
				InvocableMethod invocable = method.getAnnotation(InvocableMethod.class);
				SubstitutableMethod substitutable = method.getAnnotation(SubstitutableMethod.class);

				try {
					/* find a better method if possible */
					method = clazz.getMethod(method.getName(), method.getParameterTypes());
				} catch (NoSuchMethodException e) {
				}

				if (invocable != null) {
					String name = name(method, invocable);

					invocables.put(method, invocable);
					methods.add(method);

					if (!exported.add(name)) {
						duplicates.add(name);
					}
				}

				if (substitutable != null) {
					String name = name(method, substitutable);

					substitutables.put(method, substitutable);
					methods.add(method);

					if (!exported.add(name)) {
						duplicates.add(name);
					}
				}
			}

			for (Method method : methods) {
				int modifiers = method.getModifiers();

				if (!Modifier.isPublic(modifiers)) {
					Trace.error(String.format("method '%s' must be public", method.getName()));
				} else if ((script != null) || Modifier.isStatic(modifiers)) {
					InvocableMethod invocable = invocables.get(method);
					SubstitutableMethod substitutable = substitutables.get(method);
					ContextResource resource = null;
					String name = null;

					if ((invocable != null) && (substitutable != null)) {
						Trace.error(String.format("method '%s' can't be Invocable and Substitutable", method.getName()));
					} else if (invocable != null) {
						Class<?> returnType = method.getReturnType();

						if (boolean.class.equals(returnType) || Boolean.class.equals(returnType)) {
							name = name(method, invocable);
							resource = createInvocableResource(script, method, name);
						} else {
							Trace.error(String.format("method '%s' must return a boolean to be invocable", method.getName()));
						}
					} else if (substitutable != null) {
						name = name(method, substitutable);
						resource = createSubstitutableResource(script, method, method.getReturnType(), name);
					}

					if (resource != null) {
						if (duplicates.contains(name)) {
							Trace.error(String.format("resource '%s' is declared more than once", name));
						} else {
							resources.put(name, resource);
						}
					}
				} else {
					Trace.error(String.format("method '%s' must be static (no instance provided)", method.getName()));
				}
			}
		}

		return new ExtensionContext(clazz, Collections.unmodifiableMap(resources));
	}

	private static final String name(Method method, InvocableMethod invocable) {
		String name = invocable.value();

		if ((name == null) || name.isEmpty()) {
			name = method.getName();
		}

		return name;
	}

	private static final String name(Method method, SubstitutableMethod substitutable) {
		String name = substitutable.value();

		if ((name == null) || name.isEmpty()) {
			name = method.getName();
		}

		return name;
	}

	private static List<Method> scanMethods(Class<?> clazz, List<Method> annotated) {
		Class<?> superClazz = clazz.getSuperclass();
		Class<?>[] interfaces = clazz.getInterfaces();

		if (superClazz != null) {
			annotated = scanMethods(superClazz, annotated);
		}

		for (Class<?> impl : interfaces) {
			annotated = scanMethods(impl, annotated);
		}

		for (Method method : clazz.getDeclaredMethods()) {
			InvocableMethod invocable = method.getAnnotation(InvocableMethod.class);
			SubstitutableMethod substitutable = method.getAnnotation(SubstitutableMethod.class);

			if ((invocable != null) || (substitutable != null)) {
				annotated.add(method);
			}
		}

		return annotated;
	}

	/**
	 * register groovy script methods as api gateway resources. invocable methods
	 * can be invoked like policies. method parameters are injected from current
	 * context. substituable methods are used to return arbitrary values from an
	 * injected context. Invocable methods must return boolean and may throw
	 * CirtuitAbortException. Substituable cannot throw any exception. In such case
	 * null is returned and the exception is dropped.
	 * 
	 * @param script script to be bound
	 * @return an ExtensionContext which reflects exported methods of the given
	 *         script.
	 */
	public static ExtensionContext bind(Script script) {
		Class<? extends Script> clazz = script.getClass();

		return create(script, clazz);
	}

	public static ExtensionContext bind(Class<?> clazz) {
		return create(null, clazz);
	}

	/**
	 * register a java class instance as apigateway resources.
	 * 
	 * @param instance instance to be bound
	 * @param clazz type which contains method exports
	 * @return an ExtensionContext which reflects exported methods of the given
	 *         class.
	 */
	public static <T> ExtensionContext bind(T instance, Class<? super T> clazz) {
		return create(instance, clazz);
	}

	private static InvocableResource createInvocableResource(final Object script, Method method, String name) {
		final MethodParameter<?>[] parameters = processMethodParameters(method);

		return new InvocableResource() {
			@Override
			public boolean invoke(Circuit c, Message m) throws CircuitAbortException {
				MethodDictionary dictionary = new MethodDictionary(c, m, m);
				boolean result = false;

				try {
					Object[] resolved = new Object[parameters.length];
					String[] debug = new String[parameters.length];

					for (int index = 0; index < parameters.length; index++) {
						MethodParameter<?> resolver = parameters[index];

						if (resolver != null) {
							resolver.resolve(dictionary, index, resolved, debug);
						} else {
							resolved[index] = null;
							debug[index] = null;
						}
					}

					if (Trace.isDebugEnabled()) {
						Trace.debug(String.format("invoke '%s' (bound as '%s')", method, name));
						Trace.debug(String.format("provided parameters : '%s'", debugMethodParameters(debug)));
					}

					Object value = method.invoke(script, resolved);

					if (value instanceof Boolean) {
						result = ((Boolean) value).booleanValue();

						if (Trace.isDebugEnabled()) {
							Trace.debug(String.format("method returned '%s'", value.toString()));
						}
					} else {
						throw new CircuitAbortException("script did not return a boolean");
					}
				} catch (IllegalAccessException e) {
					throw new CircuitAbortException("script method is not accessible", e);
				} catch (IllegalArgumentException e) {
					throw new CircuitAbortException("can't invoke script method", e);
				} catch (InvocationTargetException e) {
					Throwable cause = e.getCause();

					if (cause instanceof CircuitAbortException) {
						throw (CircuitAbortException) cause;
					}

					throw new CircuitAbortException("got error when invoking method", cause);
				} catch (RuntimeException e) {
					throw new CircuitAbortException("unexpected exception", e);
				}

				return result;
			}
		};
	}

	private static <T> SubstitutableResource<T> createSubstitutableResource(final Object script, final Method method, final Class<T> returnType, String name) {
		final MethodParameter<?>[] parameters = processMethodParameters(method);

		return new SubstitutableResource<T>() {
			@Override
			public T substitute(Dictionary dict) {
				MethodDictionary dictionary = new MethodDictionary(null, dict instanceof Message ? (Message) dict : null, dict);
				T result = null;

				try {
					Object[] resolved = new Object[parameters.length];
					String[] debug = new String[parameters.length];

					for (int index = 0; index < parameters.length; index++) {
						MethodParameter<?> resolver = parameters[index];

						if (resolver != null) {
							resolver.resolve(dictionary, index, resolved, debug);
						} else {
							resolved[index] = null;
							debug[index] = null;
						}
					}

					if (Trace.isDebugEnabled()) {
						Trace.debug(String.format("substitute '%s' (bound as '%s')", method, name));
						Trace.debug(String.format("provided parameters : '%s'", debugMethodParameters(debug)));
					}

					Object value = method.invoke(script, resolved);

					if (Trace.isDebugEnabled()) {
						if (value instanceof String) {
							Trace.debug(String.format("method returned \"%s\"", ScriptHelper.encodeLiteral((String) value)));
						} else if ((value instanceof Boolean) || (value instanceof Number)) {
							Trace.debug(String.format("method returned '%s'", value.toString()));
						} else if (value != null) {
							Trace.debug(String.format("method returned a value of type '%s'", value.getClass().getName()));
						} else {
							Trace.debug("method returned null");
						}
					}

					if ((value != null) && (returnType.isAssignableFrom(value.getClass()))) {
						result = returnType.cast(value);
					}
				} catch (IllegalAccessException e) {
					Trace.error("script method is not accessible", e);
				} catch (IllegalArgumentException e) {
					Trace.error("can't invoke script method", e);
				} catch (InvocationTargetException e) {
					Throwable cause = e.getCause();

					Trace.error("got error when invoking method", cause);
				} catch (RuntimeException e) {
					Trace.error("unexpected exception", e);
				}

				return result;
			}
		};
	}

	private static MethodParameter<?>[] processMethodParameters(Method method) {
		List<MethodParameter<?>> parameters = new ArrayList<MethodParameter<?>>();
		Annotation[][] annotations = method.getParameterAnnotations();
		Class<?>[] parameterTypes = method.getParameterTypes();

		for (int index = 0; index < parameterTypes.length; index++) {
			Class<?> type = parameterTypes[index];

			/* ensure that requested type is not a java primitive */
			if (byte.class.equals(type)) {
				type = Byte.class;
			} else if (short.class.equals(type)) {
				type = Short.class;
			} else if (char.class.equals(type)) {
				type = Character.class;
			} else if (int.class.equals(type)) {
				type = Integer.class;
			} else if (long.class.equals(type)) {
				type = Long.class;
			} else if (float.class.equals(type)) {
				type = Float.class;
			} else if (double.class.equals(type)) {
				type = Double.class;
			}

			processMethodParameter(parameters, type, annotations[index]);
		}

		return parameters.toArray(new MethodParameter<?>[0]);
	}

	private static <T> void processMethodParameter(List<MethodParameter<?>> parameters, Class<T> type, Annotation[] annotations) {
		DictionaryAttribute attribute = null;
		SelectorExpression selector = null;
		MethodParameter<?> parameter = null;

		if (annotations != null) {
			for (Annotation annotation : annotations) {
				if (annotation.annotationType().equals(DictionaryAttribute.class)) {
					attribute = (DictionaryAttribute) annotation;
				} else if (annotation.annotationType().equals(SelectorExpression.class)) {
					selector = (SelectorExpression) annotation;
				}
			}
		}

		if ((attribute != null) && (selector != null)) {
			Trace.error("@DictionaryAttribute and @SelectorExpression are mutually exclusive");
		} else if (attribute != null) {
			String name = attribute.value();

			if ((name == null) || (name.isEmpty())) {
				Trace.error("attribute name is null or empty");
			} else {
				parameter = new AttributeParameter<T>(name, type);
			}
		} else if (selector != null) {
			String expression = selector.value();

			if ((expression == null) || (expression.isEmpty())) {
				Trace.error("selector expression is null or empty");
			} else {
				parameter = new SelectorParameter<T>(expression, type);
			}
		} else if (type.isAssignableFrom(MessageContextTracker.class)) {
			parameter = new MessageContextTrackerParameter();
		} else if (type.isAssignableFrom(MessageProcessor.class)) {
			parameter = new MessageProcessorParameter();
		} else if (type.isAssignableFrom(Message.class)) {
			parameter = new MessageParameter();
		} else if (type.isAssignableFrom(Circuit.class)) {
			parameter = new CircuitParameter();
		} else if (type.isAssignableFrom(Dictionary.class)) {
			parameter = new DictionaryParameter();
		} else {
			Trace.error(String.format("can't inject type '%s'", type.getName()));
		}

		parameters.add(parameter);
	}

	private static String debugMethodParameters(String[] debug) {
		StringBuilder builder = new StringBuilder();
		builder.append('[');

		for (int index = 0; index < debug.length; index++) {
			if (index > 0) {
				builder.append(',');
			}

			if (debug[index] == null) {
				builder.append("null");
			} else {
				builder.append(debug[index]);
			}
		}

		builder.append(']');

		return builder.toString();
	}

	public final Map<String, ContextResource> getContextResources() {
		return resources;
	}

	@Override
	public final ContextResource getContextResource(String name) {
		return resources.get(name);
	}

	@Override
	public final boolean invoke(Circuit c, Message m, String name) throws CircuitAbortException {
		ContextResource resource = getContextResource(m, name);

		if (!(resource instanceof InvocableResource)) {
			throw new CircuitAbortException(String.format("resource '%s' is not invocable", name));
		}

		return ((InvocableResource) resource).invoke(c, m);
	}

	@Override
	public final Object substitute(Dictionary dict, String name) {
		ContextResource resource = getContextResource(dict, name);
		Object result = null;

		if (resource instanceof SubstitutableResource) {
			result = ((SubstitutableResource<?>) resource).substitute(dict);
		} else {
			Trace.error(String.format("resource '%s' is not substitutable", name));
		}

		return result;
	}

	/**
	 * @return the class loader used for the underlying class
	 */
	public final ClassLoader getClassLoader() {
		return clazz.getClassLoader();
	}

	private static class MethodDictionary implements Dictionary {
		private final Map<String, Object> properties;

		private MethodDictionary(Circuit c, Message m, Dictionary dict) {
			Map<String, Object> properties = new HashMap<String, Object>();

			properties.put("circuit", c);
			properties.put("message", m);
			properties.put("dictionary", dict);

			this.properties = Collections.unmodifiableMap(properties);
		}

		public Message getMessage() {
			return (Message) get("message");
		}

		public MessageContextTracker getMessageContextTracker() {
			return MessageContextTracker.getMessageContextTracker(getMessage());
		}

		public MessageProcessor getMessageProcessor() {
			MessageContextTracker tracker = getMessageContextTracker();

			return tracker == null ? null : tracker.getMessageProcessor();
		}

		public Circuit getCircuit() {
			Circuit c = (Circuit) get("circuit");

			if (c == null) {
				MessageContextTracker tracker = getMessageContextTracker();

				c = tracker == null ? null : tracker.getCircuit();
			}

			return c;
		}

		public Dictionary getDictionary() {
			return (Dictionary) get("dictionary");
		}

		@Override
		public Object get(String name) {
			return properties.get(name);
		}
	}

	private static abstract class MethodParameter<T> {
		protected abstract T resolve(MethodDictionary dictionary);

		protected abstract String debug(T resolved);

		protected void resolve(MethodDictionary dictionary, int index, Object[] resolved, String[] debug) {
			T value = resolve(dictionary);

			resolved[index] = value;
			debug[index] = Trace.isDebugEnabled() ? debug(value) : null;
		}
	}

	private static class DictionaryParameter extends MethodParameter<Dictionary> {
		@Override
		protected Dictionary resolve(MethodDictionary dictionary) {
			return dictionary.getDictionary();
		}

		@Override
		protected String debug(Dictionary dict) {
			return dict instanceof Message ? "Message" : "Dictionary";
		}
	}

	private static class MessageContextTrackerParameter extends MethodParameter<MessageContextTracker> {
		@Override
		protected MessageContextTracker resolve(MethodDictionary dictionary) {
			return dictionary.getMessageContextTracker();
		}

		@Override
		protected String debug(MessageContextTracker tracker) {
			return "MessageContextTracker";
		}
	}

	private static class MessageProcessorParameter extends MethodParameter<MessageProcessor> {
		@Override
		protected MessageProcessor resolve(MethodDictionary dictionary) {
			return dictionary.getMessageProcessor();
		}

		@Override
		protected String debug(MessageProcessor processor) {
			return "MessageProcessor";
		}
	}

	private static class MessageParameter extends MethodParameter<Message> {
		@Override
		protected Message resolve(MethodDictionary dictionary) {
			return dictionary.getMessage();
		}

		@Override
		protected String debug(Message message) {
			return "Message";
		}
	}

	private static class CircuitParameter extends MethodParameter<Circuit> {
		@Override
		protected Circuit resolve(MethodDictionary dictionary) {
			return dictionary.getCircuit();
		}

		@Override
		protected String debug(Circuit circuit) {
			return "Circuit";
		}
	}

	private static class AttributeParameter<T> extends MethodParameter<T> {
		private final Selector<T> selector;
		private final String attributeName;

		private AttributeParameter(String attributeName, Class<T> type) {
			this.selector = SelectorResource.fromLiteral(String.format("${commons:value(dictionary[\"%s\"])}", attributeName), type, true);
			this.attributeName = attributeName;
		}

		@Override
		protected T resolve(MethodDictionary dictionary) {
			return selector.substitute(dictionary);
		}

		@Override
		protected String debug(T value) {
			String debug = null;

			if (value instanceof String) {
				debug = String.format("\"%s\"", ScriptHelper.encodeLiteral((String) value));
			} else if ((value instanceof Boolean) || (value instanceof Number)) {
				debug = String.format("%s", value.toString());
			} else if (value != null) {
				debug = attributeName;
			}

			return debug;
		}
	}

	private static class SelectorParameter<T> extends MethodParameter<T> {
		private final Selector<T> selector;
		private final String expression;

		private SelectorParameter(String expression, Class<T> type) {
			/* use the commons:value() function to avoid the missing property behaviour */
			this.selector = SelectorResource.fromLiteral(String.format("${commons:value(%s)}", expression), type, true);
			this.expression = expression;
		}

		@Override
		protected T resolve(MethodDictionary dictionary) {
			return selector.substitute(dictionary.getDictionary());
		}

		@Override
		protected String debug(T value) {
			String debug = null;

			if (value instanceof String) {
				debug = String.format("\"%s\"", ScriptHelper.encodeLiteral((String) value));
			} else if ((value instanceof Boolean) || (value instanceof Number)) {
				debug = String.format("%s", value.toString());
			} else if (value != null) {
				debug = String.format("${%s}", expression);
			}

			return debug;
		}
	}
}
