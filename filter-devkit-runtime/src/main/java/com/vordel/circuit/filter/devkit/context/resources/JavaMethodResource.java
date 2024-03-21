package com.vordel.circuit.filter.devkit.context.resources;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Supplier;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.context.annotations.DictionaryAttribute;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionFunction;
import com.vordel.circuit.filter.devkit.context.annotations.InvocableMethod;
import com.vordel.circuit.filter.devkit.context.annotations.SelectorExpression;
import com.vordel.circuit.filter.devkit.context.annotations.SubstitutableMethod;
import com.vordel.circuit.filter.devkit.script.ScriptHelper;
import com.vordel.common.Dictionary;
import com.vordel.el.ContextResourceResolver;
import com.vordel.el.Selector;
import com.vordel.trace.Trace;

import groovy.lang.Script;

public abstract class JavaMethodResource implements ContextResource {
	protected final Object instance;
	protected final Method method;

	protected final String resourceName;
	protected final String filterName;

	private JavaMethodResource(Object instance, Method method, String resourceName, String filterName) {
		this.method = method;

		this.resourceName = resourceName;
		this.filterName = filterName;
		this.instance = instance;
	}

	/**
	 * register groovy script methods as api gateway resources. Substitutable
	 * methods are used to return arbitrary values from an injected context.
	 * Invocable methods must return boolean and may throw CirtuitAbortException.
	 * Substituable cannot throw any exception. In such case null is returned and
	 * the exception is dropped.
	 * 
	 * @param resources  script attached resources
	 * @param script     script to be bound
	 * @param filterName name of filter hosting the script (for debugging purposes
	 */
	public static void reflectGroovy(Map<String, ContextResource> resources, Script script, String filterName) {
		if (script != null) {
			Class<? extends Script> clazz = script.getClass();

			reflect(resources, script, clazz, filterName);
		}
	}

	/**
	 * entry to register resources from an object instance
	 * 
	 * @param resources script attached resources
	 * @param instance  extension to be bound to the script
	 */
	public static void reflectInstance(Map<String, ContextResource> resources, Object instance) {
		if (instance != null) {
			Class<?> clazz = instance.getClass();

			reflect(resources, instance, clazz, null);
		}
	}

	public static <T> void reflectInstance(Map<String, ContextResource> resources, T instance, Class<? extends T> clazz) {
		reflect(resources, instance, clazz, null);
	}

	public static void reflectClass(Map<String, ContextResource> resources, Class<?> clazz) {
		reflect(resources, null, clazz, null);
	}

	/**
	 * reflect the given class and create associated resources.
	 * 
	 * @param <T>        type parameter used to link the provided instance and
	 *                   provided class
	 * @param resources  map of resources. reflected resources will be added to this
	 *                   map
	 * @param instance   instance to be reflected
	 * @param clazz      class definition of the given instance
	 * @param filterName name of the filter if reflecting a Groovy Script
	 */
	private static final <T> void reflect(Map<String, ContextResource> resources, T instance, Class<? extends T> clazz, String filterName) {
		if (clazz != null) {
			Map<String, Supplier<ContextResource>> exported = new HashMap<String, Supplier<ContextResource>>();
			Map<Method, AnnotatedMethod> methods = new HashMap<Method, AnnotatedMethod>();

			Set<String> duplicates = new HashSet<String>();

			/* scan methods with relevant annotations */
			scanAnnotatedMethods(clazz, methods);

			/*
			 * create a map of resource suppliers (this allow to handle duplicates in one
			 * step)
			 */

			for (AnnotatedMethod annotated : methods.values()) {
				Method method = annotated.getMethod();

				if ((instance != null) || Modifier.isStatic(method.getModifiers())) {
					InvocableMethod invocable = annotated.getInvocableMethod();
					ExtensionFunction function = annotated.getExtensionFunction();
					SubstitutableMethod substitutable = annotated.getSubstitutableMethod();

					if (function != null) {
						if ((invocable != null) || (substitutable != null)) {
							Trace.error(String.format("method '%s' : ExtensionFunction can't be combined with Invocable or Substitutable", method.getName()));
						} else {
							String resourceName = name(method, function);

							registerResourceSupplier(exported, duplicates, method, resourceName, () -> {
								Class<?> dictionaryType = getFunctionDictionaryType(method);

								if (dictionaryType == null) {
									if (filterName != null) {
										Trace.error(String.format("first argument of '%s' in script '%s' must be a dictionary", method.getName(), filterName));
									} else {
										Trace.error(String.format("first argument of '%s' must be a dictionary", method));
									}

									return null;
								}

								return new FunctionMethodResource(instance, annotated, dictionaryType, resourceName, filterName);
							});
						}
					} else {
						Class<?> returnType = method.getReturnType();

						if (invocable != null) {
							if (boolean.class.equals(returnType) || Boolean.class.equals(returnType)) {
								String invocableName = name(method, invocable);
								Supplier<ContextResource> resource = null;

								if (substitutable != null) {
									String substitutableName = name(method, substitutable);

									if (invocableName.equals(substitutableName)) {
										/*
										 * special case Invocable or Substitutable returning boolean with same name on
										 * same method
										 */
										resource = () -> {
											return new BooleanMethodResource(instance, annotated, invocableName, filterName);
										};

										/* do not try to create a substitutable resource */
										substitutable = null;
									}
								}

								if (resource == null) {
									resource = () -> {
										return new InvocableMethodResource(instance, annotated, invocableName, filterName);
									};
								}

								registerResourceSupplier(exported, duplicates, method, invocableName, resource);
							} else {
								Trace.error(String.format("method '%s' must return a boolean to be invocable", method.getName()));
							}
						}

						if (substitutable != null) {
							String substitutableName = name(method, substitutable);

							registerResourceSupplier(exported, duplicates, method, substitutableName, () -> {
								/* use diamond operator to avoid writing a static method */
								return new SubstitutableMethodResource<>(instance, annotated, returnType, substitutableName, filterName);
							});
						}
					}
				} else {
					Trace.error(String.format("method '%s' must be static (no instance provided)", method.getName()));
				}
			}

			if (resources != null) {
				/* resolves all exported resources by calling supliers */

				for (Entry<String, Supplier<ContextResource>> entry : exported.entrySet()) {
					String resourceName = entry.getKey();
					ContextResource resource = entry.getValue().get();

					resources.put(resourceName, resource);
				}
			}
		}
	}

	private static final void registerResourceSupplier(Map<String, Supplier<ContextResource>> exported, Set<String> duplicates, Method method, String resourceName, Supplier<ContextResource> resource) {
		if (duplicates.contains(resourceName) || exported.containsKey(resourceName)) {
			Trace.error(String.format("multiple definitions of resource '%s' (method '%s')", resourceName, method.getName()));

			duplicates.add(resourceName);
			exported.remove(resourceName);
		} else {
			exported.put(resourceName, resource);
		}
	}

	/**
	 * retrieve name of resource from annotation
	 * 
	 * @param method    reflected method
	 * @param invocable method annotation
	 * @return name of resource
	 */
	private static final String name(Method method, InvocableMethod invocable) {
		String name = invocable.value();

		if ((name == null) || name.isEmpty()) {
			name = method.getName();
		}

		return name;
	}

	/**
	 * retrieve name of resource from annotation
	 * 
	 * @param method   reflected method
	 * @param function method annotation
	 * @return name of resource
	 */
	private static final String name(Method method, ExtensionFunction function) {
		String name = function.value();

		if ((name == null) || name.isEmpty()) {
			name = method.getName();
		}

		return name;
	}

	/**
	 * retrieve name of resource from annotation
	 * 
	 * @param method        reflected method
	 * @param substitutable method annotation
	 * @return name of resource
	 */
	private static final String name(Method method, SubstitutableMethod substitutable) {
		String name = substitutable.value();

		if ((name == null) || name.isEmpty()) {
			name = method.getName();
		}

		return name;
	}

	/**
	 * recursively scan class for annotated methods
	 * 
	 * @param clazz   current class
	 * @param methods current public methods
	 */
	private static void scanAnnotatedMethods(Class<?> clazz, Map<Method, AnnotatedMethod> methods) {
		Set<Class<?>> seen = new HashSet<Class<?>>();

		scanMethods(clazz, clazz, methods, seen);
		filterAnnotatedMethods(methods);
	}

	/**
	 * recursively scan class for annotated methods. This method takes care of
	 * checking if class has already been reflected. Also it returns a map or
	 * annotated method using the top concrete implementation as key.
	 * 
	 * @param base    base top class
	 * @param clazz   current class
	 * @param methods current public methods
	 * @param seen    already reflected classes and interfaces
	 */
	private static void scanMethods(Class<?> base, Class<?> clazz, Map<Method, AnnotatedMethod> methods, Set<Class<?>> seen) {
		if (seen.add(clazz)) {
			Class<?> superClazz = clazz.getSuperclass();
			Class<?>[] interfaces = clazz.getInterfaces();

			if (superClazz != null) {
				/* start scanning super class */
				scanMethods(base, superClazz, methods, seen);
			}

			for (Class<?> impl : interfaces) {
				/* eventually, apply overrides from interfaces */
				scanMethods(base, impl, methods, seen);
			}

			/* finally, apply this class methods */

			for (Method method : clazz.getDeclaredMethods()) {
				AnnotatedMethod parent = null;
				Method top = null;

				try {
					top = base.getMethod(method.getName(), method.getParameterTypes());
					parent = methods.get(top);
				} catch (NoSuchMethodException e) {
					/* ignore, method is not public */
				}

				AnnotatedMethod annotated = new AnnotatedMethod(parent, method, top);

				if ((top == null) && annotated.hasExtensionAnnotation() && (!Modifier.isPublic(method.getModifiers()))) {
					/* if this method is not public and it have extension annotations display it */
					Trace.error(String.format("annotated method '%s' must be public", method.getName()));
				} else if (top != null) {
					/* if this method has a exposed public member, save it */
					methods.put(top, annotated);
				}
			}
		}
	}

	/**
	 * cleanup method map after scan (remove methods which are not annotated
	 * 
	 * @param methods public reflected methods
	 */
	private static void filterAnnotatedMethods(Map<Method, AnnotatedMethod> methods) {
		Iterator<AnnotatedMethod> iterator = methods.values().iterator();

		while (iterator.hasNext()) {
			if (!iterator.next().hasExtensionAnnotation()) {
				iterator.remove();
			}
		}
	}

	/**
	 * Analyse invocable and substitutable parameters for parameter injection.
	 * Purpose of this method is to preprocess injection of parameters to save time
	 * when invocation is requested.
	 * 
	 * @param method annotated method to be analysed
	 * @return parameter injection information.
	 */
	private static InjectableParameter<?>[] processInjectableParameters(AnnotatedMethod method) {
		Annotation[][] annotations = method.getParameterAnnotations();
		Class<?>[] parameterTypes = method.getMethod().getParameterTypes();
		InjectableParameter<?>[] parameters = new InjectableParameter<?>[parameterTypes.length];

		for (int index = 0; index < parameters.length; index++) {
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

			parameters[index] = processInjectableParameter(type, annotations[index]);
		}

		return parameters;
	}

	/**
	 * create parameter injector from requested annotated class
	 * 
	 * @param <T>         type parameter locked to requested type (needed for
	 *                    injector constructor)
	 * @param type        requested parameter type
	 * @param annotations available annotations on method parameter
	 * @return a new parameter injector.
	 */
	private static <T> InjectableParameter<?> processInjectableParameter(Class<T> type, Annotation[] annotations) {
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

		return parameter;
	}

	/**
	 * Check if the first argument of function is a dictionary.
	 * 
	 * @param method method to be checked
	 * @return dictionary class or null if not dictionary.
	 */
	private static Class<?> getFunctionDictionaryType(Method method) {
		Class<?>[] parameterTypes = method.getParameterTypes();

		if (parameterTypes.length < 1) {
			return null;
		}

		Class<?> dictionaryType = parameterTypes[0];

		return Dictionary.class.isAssignableFrom(dictionaryType) ? dictionaryType : null;
	}

	/**
	 * prepend function dictionary to arguments
	 * 
	 * @param msg      message argument
	 * @param dict     dictionary (general selector dictionary type. Usually same
	 *                 object as given msg parameter)
	 * @param dictType dictionary requested by function
	 * @param args     original function call argument list
	 * @return argument list prepended by best dictionary parameter (or
	 *         <code>null</code> if not compatible)
	 */
	private static Object[] setupFunctionParams(Message msg, Dictionary dict, Class<?> dictType, Object[] args) {
		List<Object> params = new ArrayList<Object>(args.length + 1);
		Class<?> clazz = msg == null ? Message.class : msg.getClass();

		if (dictType.isAssignableFrom(clazz)) {
			params.add(msg);
		} else if (dictType.isAssignableFrom(Dictionary.class)) {
			params.add(dict);
		} else {
			params.add(null);
		}

		for (Object arg : args) {
			params.add(arg);
		}

		return params.toArray();
	}

	protected Object invokeMethod(String kind, Object... args) throws InvocationTargetException {
		if (filterName != null) {
			Trace.debug(String.format("%s '%s' from script '%s' (bound as '%s')", kind, method.getName(), filterName, resourceName));
		} else {
			Trace.debug(String.format("%s '%s' (bound as '%s')", kind, method, resourceName));
		}

		try {
			Object result = method.invoke(instance, args);

			if (Trace.isDebugEnabled()) {
				Class<?> returnType = method.getReturnType();

				if (returnType.equals(void.class) || returnType.equals(Void.class)) {
					Trace.debug(String.format("method '%s' did not return result (void)", method.getName()));
				} else if (result instanceof String) {
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
			throw new IllegalStateException("method is not accessible", e);
		}
	}

	private Object invokeMethod(InjectableParameter<?>[] parameters, Dictionary dict, String kind) throws InvocationTargetException {
		MethodDictionary dictionary = new MethodDictionary(dict instanceof Message ? (Message) dict : null, dict);
		Object[] resolved = new Object[parameters.length];

		for (int index = 0; index < parameters.length; index++) {
			InjectableParameter<?> resolver = parameters[index];

			if (resolver != null) {
				resolver.resolve(dictionary, index, resolved);
			} else {
				resolved[index] = null;
			}
		}

		return invokeMethod(kind, resolved);
	}

	protected static boolean invokeResource(JavaMethodResource resource, InjectableParameter<?>[] parameters, Message m) throws CircuitAbortException {
		try {
			Object result = resource.invokeMethod(parameters, m, "invoke");

			if (result instanceof Boolean) {
				return (Boolean) result;
			}

			throw new CircuitAbortException("method did not return a boolean");
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();

			if (cause instanceof CircuitAbortException) {
				throw (CircuitAbortException) cause;
			}

			if (Trace.isDebugEnabled()) {
				Trace.debug(String.format("method '%s' thrown exception ", resource.method.getName()), cause);
			}

			throw new CircuitAbortException("got error when invoking method", cause);
		} catch (RuntimeException e) {
			throw new CircuitAbortException("unexpected exception", e);
		}
	}

	protected static <T> T substituteResource(JavaMethodResource resource, Class<T> returnType, InjectableParameter<?>[] parameters, Dictionary dict) {
		try {
			Object result = resource.invokeMethod(parameters, dict, "substitute");

			if ((result != null) && (returnType.isAssignableFrom(result.getClass()))) {
				return returnType.cast(result);
			}

			return null;
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();

			/*
			 * since exceptions are silenced for substitutables, do not report an error but
			 * show the stacktrace in debug
			 */
			if (Trace.isDebugEnabled()) {
				Trace.debug(String.format("method '%s' thrown exception", resource.method.getName()), cause);
			}
		} catch (RuntimeException e) {
			/*
			 * since exceptions are silenced for substitutables, do not report an error but
			 * show the stacktrace in debug
			 */
			if (Trace.isDebugEnabled()) {
				Trace.debug(String.format("unexpected exception when calling method '%s'", resource.method.getName()), e);
			}
		}

		return null;
	}

	public static class InvocableMethodResource extends JavaMethodResource implements InvocableResource {
		protected final InjectableParameter<?>[] parameters;

		private InvocableMethodResource(Object instance, AnnotatedMethod annotated, String resourceName, String filterName) {
			super(instance, annotated.getMethod(), resourceName, filterName);

			this.parameters = processInjectableParameters(annotated);
		}

		@Override
		public boolean invoke(Message m) throws CircuitAbortException {
			return invokeResource(this, parameters, m);
		}
	}

	public static class SubstitutableMethodResource<T> extends JavaMethodResource implements SubstitutableResource<T> {
		protected final InjectableParameter<?>[] parameters;
		protected final Class<T> returnType;

		private SubstitutableMethodResource(Object instance, AnnotatedMethod annotated, Class<T> returnType, String resourceName, String filterName) {
			super(instance, annotated.getMethod(), resourceName, filterName);

			this.returnType = returnType;
			this.parameters = processInjectableParameters(annotated);
		}

		@Override
		public T substitute(Dictionary dict) {
			return substituteResource(this, returnType, parameters, dict);
		}
	}

	public static class BooleanMethodResource extends InvocableMethodResource implements SubstitutableResource<Boolean> {
		private BooleanMethodResource(Object instance, AnnotatedMethod annotated, String resourceName, String filterName) {
			super(instance, annotated, resourceName, filterName);
		}

		@Override
		public Boolean substitute(Dictionary dict) {
			return substituteResource(this, Boolean.class, parameters, dict);
		}
	}

	public static class FunctionMethodResource extends JavaMethodResource implements FunctionResource {
		private final Class<?> dictionaryType;

		private FunctionMethodResource(Object instance, AnnotatedMethod annotated, Class<?> dictionaryType, String resourceName, String filterName) {
			super(instance, annotated.getMethod(), resourceName, filterName);

			this.dictionaryType = dictionaryType;
		}

		@Override
		public Object invoke(Dictionary dict, Object... args) throws CircuitAbortException {
			/* update arguments and prepend message/dictionary */
			Message msg = dict instanceof Message ? (Message) dict : null;
			Object[] params = ContextResourceResolver.coerceFunctionArguments(method, setupFunctionParams(msg, dict, dictionaryType, args));

			try {
				return invokeMethod("call", params);
			} catch (InvocationTargetException e) {
				Throwable cause = e.getCause();

				if (cause instanceof CircuitAbortException) {
					throw (CircuitAbortException) cause;
				}

				if (Trace.isDebugEnabled()) {
					Trace.debug(String.format("method '%s' thrown exception ", method.getName()), cause);
				}

				throw new CircuitAbortException("got error when invoking method", cause);
			} catch (RuntimeException e) {
				throw new CircuitAbortException("unexpected exception", e);
			}
		}
	}

	private static class AnnotatedMethod {
		private final AnnotatedMethod parent;
		private final Method method;

		private final ExtensionFunction function;
		private final InvocableMethod invocable;
		private final SubstitutableMethod substitutable;
		private final Method top;

		private AnnotatedMethod(AnnotatedMethod parent, Method method, Method top) {
			this.method = method;
			this.top = top;
			this.parent = parent;

			this.function = method.getAnnotation(ExtensionFunction.class);
			this.invocable = method.getAnnotation(InvocableMethod.class);
			this.substitutable = method.getAnnotation(SubstitutableMethod.class);
		}

		public Method getMethod() {
			return top;
		}

		public boolean hasExtensionAnnotation() {
			return (function != null) || (invocable != null) || (substitutable != null);
		}

		public ExtensionFunction getExtensionFunction() {
			if (hasExtensionAnnotation()) {
				return function;
			} else if (parent != null) {
				return parent.getExtensionFunction();
			}

			return null;
		}

		public InvocableMethod getInvocableMethod() {
			if (hasExtensionAnnotation()) {
				return invocable;
			} else if (parent != null) {
				return parent.getInvocableMethod();
			}

			return null;
		}

		public SubstitutableMethod getSubstitutableMethod() {
			if (hasExtensionAnnotation()) {
				return substitutable;
			} else if (parent != null) {
				return parent.getSubstitutableMethod();
			}

			return null;
		}

		public Annotation[][] getParameterAnnotations() {
			int length = method.getParameterCount();
			Annotation[][] annotations = new Annotation[length][];

			for (int index = 0; index < length; index++) {
				annotations[index] = getParameterAnnotations(index);
			}

			return annotations;
		}

		public Annotation[] getParameterAnnotations(int index) {
			Annotation[][] annotations = method.getParameterAnnotations();

			for (Annotation annotation : annotations[index]) {
				Class<? extends Annotation> annotationType = annotation.annotationType();

				if (annotationType.equals(DictionaryAttribute.class) || annotationType.equals(SelectorExpression.class)) {
					return annotations[index];
				}
			}

			/* no annotations defined for parameter return default result for method */
			return parent == null ? annotations[index] : parent.getParameterAnnotations(index);
		}
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

	private abstract static class InjectableParameter<T> {
		protected abstract T resolve(MethodDictionary dictionary);

		protected void resolve(MethodDictionary dictionary, int index, Object[] resolved) {
			T value = resolve(dictionary);

			resolved[index] = value;
		}
	}

	private static class DictionaryParameter extends InjectableParameter<Dictionary> {
		@Override
		protected Dictionary resolve(MethodDictionary dictionary) {
			return dictionary.getDictionary();
		}
	}

	private static class MessageParameter extends InjectableParameter<Message> {
		@Override
		protected Message resolve(MethodDictionary dictionary) {
			return dictionary.getMessage();
		}
	}

	private static class AttributeParameter<T> extends InjectableParameter<T> {
		private final Selector<T> selector;

		private AttributeParameter(String attributeName, Class<T> type) {
			this.selector = SelectorResource.fromExpression(String.format("dictionary[\"%s\"]", escapeQuotes(attributeName)), type);
		}

		private static final String escapeQuotes(String attributeName) {
			StringBuilder out = new StringBuilder();

			for (char c : attributeName.toCharArray()) {
				if (c == '"') {
					out.append('\\');
				}

				out.append(c);
			}

			return out.toString();
		}

		@Override
		protected T resolve(MethodDictionary dictionary) {
			return selector.substitute(dictionary);
		}
	}

	private static class SelectorParameter<T> extends InjectableParameter<T> {
		private final Selector<T> selector;

		private SelectorParameter(String expression, Class<T> type) {
			this.selector = SelectorResource.fromExpression(expression, type);
		}

		@Override
		protected T resolve(MethodDictionary dictionary) {
			return selector.substitute(dictionary.getDictionary());
		}
	}

}
