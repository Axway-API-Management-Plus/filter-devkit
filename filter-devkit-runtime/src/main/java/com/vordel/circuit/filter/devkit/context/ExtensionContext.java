package com.vordel.circuit.filter.devkit.context;

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
import com.vordel.circuit.filter.devkit.context.annotations.DictionaryAttribute;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionFunction;
import com.vordel.circuit.filter.devkit.context.annotations.InvocableMethod;
import com.vordel.circuit.filter.devkit.context.annotations.SelectorExpression;
import com.vordel.circuit.filter.devkit.context.annotations.SubstitutableMethod;
import com.vordel.circuit.filter.devkit.context.resources.AbstractContextResourceProvider;
import com.vordel.circuit.filter.devkit.context.resources.ContextResource;
import com.vordel.circuit.filter.devkit.context.resources.FunctionResource;
import com.vordel.circuit.filter.devkit.context.resources.InvocableResource;
import com.vordel.circuit.filter.devkit.context.resources.SelectorResource;
import com.vordel.circuit.filter.devkit.context.resources.SubstitutableResource;
import com.vordel.circuit.filter.devkit.script.ScriptHelper;
import com.vordel.common.Dictionary;
import com.vordel.el.ContextResourceResolver;
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
			Map<Method, ExtensionFunction> functions = new HashMap<Method, ExtensionFunction>();
			Map<Method, SubstitutableMethod> substitutables = new HashMap<Method, SubstitutableMethod>();
			Set<Method> methods = new HashSet<Method>();

			Set<String> exported = new HashSet<String>();
			Set<String> duplicates = new HashSet<String>();

			for (Method method : scanMethods(clazz, new ArrayList<Method>())) {
				InvocableMethod invocable = method.getAnnotation(InvocableMethod.class);
				ExtensionFunction function = method.getAnnotation(ExtensionFunction.class);
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

				if (function != null) {
					String name = name(method, function);

					functions.put(method, function);
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
					ExtensionFunction function = functions.get(method);
					SubstitutableMethod substitutable = substitutables.get(method);
					ContextResource resource = null;
					String name = null;

					if (hasMultipleAnnotations(invocable, function, substitutable)) {
						Trace.error(String.format("method '%s' can only be one of Invocable or Substitutable or ExtensionFunction", method.getName()));
					} else if (invocable != null) {
						Class<?> returnType = method.getReturnType();

						if (boolean.class.equals(returnType) || Boolean.class.equals(returnType)) {
							name = name(method, invocable);
							resource = createInvocableResource(script, method, name);
						} else {
							Trace.error(String.format("method '%s' must return a boolean to be invocable", method.getName()));
						}
					} else if (function != null) {
						name = name(method, function);
						resource = createFunctionResource(script, method, name);
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

	private static boolean hasMultipleAnnotations(InvocableMethod invocable, ExtensionFunction function, SubstitutableMethod substitutable) {
		int count = 0;

		count += invocable == null ? 0 : 1;
		count += function == null ? 0 : 1;
		count += substitutable == null ? 0 : 1;

		return count > 1;
	}

	private static final String name(Method method, InvocableMethod invocable) {
		String name = invocable.value();

		if ((name == null) || name.isEmpty()) {
			name = method.getName();
		}

		return name;
	}

	private static final String name(Method method, ExtensionFunction function) {
		String name = function.value();

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
			ExtensionFunction function = method.getAnnotation(ExtensionFunction.class);
			SubstitutableMethod substitutable = method.getAnnotation(SubstitutableMethod.class);

			if ((invocable != null) || (function != null) || (substitutable != null)) {
				annotated.add(method);
			}
		}

		return annotated;
	}

	/**
	 * register groovy script methods as api gateway resources. invocable methods
	 * can be invoked like policies. method parameters are injected from current
	 * context. substitutable methods are used to return arbitrary values from an
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
	 * @param <T>      class type
	 * @param instance instance to be bound
	 * @param clazz    type which contains method exports
	 * @return an ExtensionContext which reflects exported methods of the given
	 *         class.
	 */
	public static <T> ExtensionContext bind(T instance, Class<? super T> clazz) {
		return create(instance, clazz);
	}

	private static InvocableResource createInvocableResource(final Object script, final Method method, final String name) {
		final InjectableParameter<?>[] parameters = processInjectableParameters(method);

		return new InvocableResource() {
			@Override
			public Boolean invoke(Message m) throws CircuitAbortException {
				MethodDictionary dictionary = new MethodDictionary(m, m);
				boolean result = false;

				try {
					Object[] resolved = new Object[parameters.length];
					String[] debug = new String[parameters.length];

					for (int index = 0; index < parameters.length; index++) {
						InjectableParameter<?> resolver = parameters[index];

						if (resolver != null) {
							resolver.resolve(dictionary, index, resolved, debug);
						} else {
							resolved[index] = null;
							debug[index] = null;
						}
					}

					if (Trace.isDebugEnabled()) {
						Trace.debug(String.format("invoke '%s' (bound as '%s')", method, name));
						Trace.debug(String.format("provided parameters : '%s'", debugInjectableParameters(debug)));
					}

					Object value = method.invoke(script, resolved);

					if (value instanceof Boolean) {
						result = ((Boolean) value).booleanValue();

						if (Trace.isDebugEnabled()) {
							Trace.debug(String.format("method '%s' returned '%s'", method.getName(), value.toString()));
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

	private static FunctionResource createFunctionResource(final Object script, final Method method, final String name) {
		/* first argument of functions is the context dictionary, can be a Message */
		final Class<?> dictionaryType = getFunctionDictionaryType(method);

		return new FunctionResource() {
			@Override
			public Object invoke(Dictionary dict, Object... args) throws CircuitAbortException {
				try {
					Object[] params = null;

					if (dictionaryType != null) {
						/* update arguments and prepend message/dictionary */
						Message msg = dict instanceof Message ? (Message) dict : null;

						params = ContextResourceResolver.coerceFunctionArguments(method, setupParams(msg, dict, dictionaryType, args));
					} else {
						params = ContextResourceResolver.coerceFunctionArguments(method, args);
					}

					if (Trace.isDebugEnabled()) {
						Trace.debug(String.format("substitute '%s' (bound as '%s')", method, name));
					}

					Object result = method.invoke(script, params);

					if (Trace.isDebugEnabled()) {
						if (result instanceof String) {
							Trace.debug(String.format("method '%s' returned \"%s\"", method.getName(), ScriptHelper.encodeLiteral((String) result)));
						} else if ((result instanceof Boolean) || (result instanceof Number)) {
							Trace.debug(String.format("method '%s' returned '%s'", method.getName(), result.toString()));
						} else if (result != null) {
							Trace.debug(String.format("method '%s' returned a value of type '%s'", method.getName(), result.getClass().getName()));
						} else {
							Trace.debug(String.format("method '%s' returned null", method.getName()));
						}
					}

					return result;
				} catch (IllegalAccessException e) {
					throw new CircuitAbortException("method is not accessible", e);
				} catch (IllegalArgumentException e) {
					throw new CircuitAbortException("can't invoke method", e);
				} catch (InvocationTargetException e) {
					Throwable cause = e.getCause();

					if (cause instanceof CircuitAbortException) {
						throw (CircuitAbortException) cause;
					}

					throw new CircuitAbortException("got error when invoking method", cause);
				} catch (RuntimeException e) {
					throw new CircuitAbortException("unexpected exception", e);
				}
			}
		};
	}

	private static Class<?> getFunctionDictionaryType(Method method) {
		Class<?>[] parameterTypes = method.getParameterTypes();

		if (parameterTypes.length < 1) {
			return null;
		}

		Class<?> dictionaryType = parameterTypes[0];

		return Dictionary.class.isAssignableFrom(dictionaryType) ? dictionaryType : null;
	}

	private static Object[] setupParams(Message msg, Dictionary dict, Class<?> dictType, Object[] args) {
		List<Object> params = new ArrayList<Object>(args.length + 1);

		if (Message.class.isAssignableFrom(dictType)) {
			params.add(msg);
		} else {
			params.add(dict);
		}

		for (Object arg : args) {
			params.add(arg);
		}

		return params.toArray();
	}

	private static <T> SubstitutableResource<T> createSubstitutableResource(final Object script, final Method method, final Class<T> returnType, String name) {
		final InjectableParameter<?>[] parameters = processInjectableParameters(method);

		return new SubstitutableResource<T>() {
			@Override
			public T substitute(Dictionary dict) {
				MethodDictionary dictionary = new MethodDictionary(dict instanceof Message ? (Message) dict : null, dict);
				T result = null;

				try {
					Object[] resolved = new Object[parameters.length];
					String[] debug = new String[parameters.length];

					for (int index = 0; index < parameters.length; index++) {
						InjectableParameter<?> resolver = parameters[index];

						if (resolver != null) {
							resolver.resolve(dictionary, index, resolved, debug);
						} else {
							resolved[index] = null;
							debug[index] = null;
						}
					}

					if (Trace.isDebugEnabled()) {
						Trace.debug(String.format("substitute '%s' (bound as '%s')", method, name));
						Trace.debug(String.format("provided parameters : '%s'", debugInjectableParameters(debug)));
					}

					Object value = method.invoke(script, resolved);

					if (Trace.isDebugEnabled()) {
						if (value instanceof String) {
							Trace.debug(String.format("method '%s' returned \"%s\"", method.getName(), ScriptHelper.encodeLiteral((String) value)));
						} else if ((value instanceof Boolean) || (value instanceof Number)) {
							Trace.debug(String.format("method '%s' returned '%s'", method.getName(), value.toString()));
						} else if (value != null) {
							Trace.debug(String.format("method '%s' returned a value of type '%s'", method.getName(), value.getClass().getName()));
						} else {
							Trace.debug(String.format("method '%s' returned null", method.getName()));
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

	private static InjectableParameter<?>[] processInjectableParameters(Method method) {
		List<InjectableParameter<?>> parameters = new ArrayList<InjectableParameter<?>>();
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

			processInjectableParameter(parameters, type, annotations[index]);
		}

		return parameters.toArray(new InjectableParameter<?>[0]);
	}

	private static <T> void processInjectableParameter(List<InjectableParameter<?>> parameters, Class<T> type, Annotation[] annotations) {
		DictionaryAttribute attribute = null;
		SelectorExpression selector = null;
		InjectableParameter<?> parameter = null;

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
		} else if (type.isAssignableFrom(Message.class)) {
			parameter = new MessageParameter();
		} else if (type.isAssignableFrom(Dictionary.class)) {
			parameter = new DictionaryParameter();
		} else {
			Trace.error(String.format("can't inject type '%s'", type.getName()));
		}

		parameters.add(parameter);
	}

	private static String debugInjectableParameters(String[] debug) {
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

	/**
	 * @return the class loader used for the underlying class
	 */
	public final ClassLoader getClassLoader() {
		return clazz.getClassLoader();
	}

	private static class MethodDictionary implements Dictionary {
		private final Map<String, Object> properties;

		private MethodDictionary(Message m, Dictionary dict) {
			Map<String, Object> properties = new HashMap<String, Object>();

			properties.put("message", m);
			properties.put("dictionary", dict);

			this.properties = Collections.unmodifiableMap(properties);
		}

		public Message getMessage() {
			return (Message) get("message");
		}

		public Dictionary getDictionary() {
			return (Dictionary) get("dictionary");
		}

		@Override
		public Object get(String name) {
			return properties.get(name);
		}
	}

	private static abstract class InjectableParameter<T> {
		protected abstract T resolve(MethodDictionary dictionary);

		protected abstract String debug(T resolved);

		protected void resolve(MethodDictionary dictionary, int index, Object[] resolved, String[] debug) {
			T value = resolve(dictionary);

			resolved[index] = value;
			debug[index] = Trace.isDebugEnabled() ? debug(value) : null;
		}
	}

	private static class DictionaryParameter extends InjectableParameter<Dictionary> {
		@Override
		protected Dictionary resolve(MethodDictionary dictionary) {
			return dictionary.getDictionary();
		}

		@Override
		protected String debug(Dictionary dict) {
			return dict instanceof Message ? "Message" : "Dictionary";
		}
	}

	private static class MessageParameter extends InjectableParameter<Message> {
		@Override
		protected Message resolve(MethodDictionary dictionary) {
			return dictionary.getMessage();
		}

		@Override
		protected String debug(Message message) {
			return "Message";
		}
	}

	private static class AttributeParameter<T> extends InjectableParameter<T> {
		private final Selector<T> selector;
		private final String attributeName;

		private AttributeParameter(String attributeName, Class<T> type) {
			this.selector = SelectorResource.fromExpression(String.format("dictionary[\"%s\"]", attributeName), type);
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

	private static class SelectorParameter<T> extends InjectableParameter<T> {
		private final Selector<T> selector;
		private final String expression;

		private SelectorParameter(String expression, Class<T> type) {
			this.selector = SelectorResource.fromExpression(expression, type);
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
