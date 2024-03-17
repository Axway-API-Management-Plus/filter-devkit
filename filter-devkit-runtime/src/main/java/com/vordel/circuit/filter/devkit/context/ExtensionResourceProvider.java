package com.vordel.circuit.filter.devkit.context;

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
import java.util.Set;
import java.util.function.BiFunction;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.context.annotations.DictionaryAttribute;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionFunction;
import com.vordel.circuit.filter.devkit.context.annotations.InvocableMethod;
import com.vordel.circuit.filter.devkit.context.annotations.SelectorExpression;
import com.vordel.circuit.filter.devkit.context.annotations.SubstitutableMethod;
import com.vordel.circuit.filter.devkit.context.resources.AbstractContextResourceProvider;
import com.vordel.circuit.filter.devkit.context.resources.ContextResource;
import com.vordel.circuit.filter.devkit.context.resources.ContextResourceProvider;
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

/**
 * This class represent exported resources from Java (and Groovy) code. It will
 * expose annotated methods to the API Gateway using the
 * {@link ContextResourceProvider} interface. Exposed methods are discovered
 * using basic reflection.
 * 
 * @author rdesaintleger@axway.com
 */
public final class ExtensionResourceProvider extends AbstractContextResourceProvider {
	private final Map<String, ContextResource> resources;
	private final Class<?> clazz;

	/**
	 * Private constructor. Called when exposed methods has been discovered
	 * 
	 * @param clazz     class which has been reflected
	 * @param resources exposed resources for this class.
	 */
	private ExtensionResourceProvider(Class<?> clazz, Map<String, ContextResource> resources) {
		this.resources = resources;
		this.clazz = clazz;
	}

	/**
	 * package private create() call. Used by the {@link ExtensionScanner}. This
	 * method will reflect provided class parameter and will create resources
	 * according to the given instance.
	 * 
	 * @param <T>    type parameter used to link the provided instance and provided
	 *               class
	 * @param module module instance to be reflected
	 * @param clazz  class definition of the module instance
	 * @return ExtensionResourceProvider containing exposed methods for the given
	 *         module
	 */
	static <T> ExtensionResourceProvider create(T module, Class<? extends T> clazz) {
		return create(module, clazz, null);
	}

	/**
	 * private create call. This method will reflect provided class parameter and
	 * will create resources according to the given instance. The filterName
	 * parameter is used for debugging purposes.
	 * 
	 * @param <T>        type parameter used to link the provided instance and
	 *                   provided class
	 * @param instance   instance which holds non static exported methods
	 * @param clazz      Class object corresponding to the given instance
	 * @param filterName used when the instance is a script
	 * @return ExtensionResourceProvider containing exposed methods for the given
	 *         instance
	 */
	private static <T> ExtensionResourceProvider create(T instance, Class<? extends T> clazz, String filterName) {
		Map<String, ContextResource> resources = new HashMap<String, ContextResource>();

		reflect(resources, instance, clazz, filterName);

		return new ExtensionResourceProvider(clazz, Collections.unmodifiableMap(resources));
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
	private static <T> void reflect(Map<String, ContextResource> resources, T instance, Class<? extends T> clazz, String filterName) {
		if (clazz != null) {
			Map<Method, AnnotatedMethod> methods = new HashMap<Method, AnnotatedMethod>();

			Set<String> exported = new HashSet<String>();
			Set<String> duplicates = new HashSet<String>();

			/* retrieve all annotated methods for this class */
			ExtensionResourceProvider.scanAnnotatedMethod(clazz, methods);

			for (AnnotatedMethod annotated : methods.values()) {
				InvocableMethod invocable = annotated.getInvocableMethod();
				ExtensionFunction function = annotated.getExtensionFunction();
				SubstitutableMethod substitutable = annotated.getSubstitutableMethod();
				Method method = annotated.getMethod();

				addAnnotatedMethod(exported, duplicates, method, invocable, ExtensionResourceProvider::name);
				addAnnotatedMethod(exported, duplicates, method, function, ExtensionResourceProvider::name);
				addAnnotatedMethod(exported, duplicates, method, substitutable, ExtensionResourceProvider::name);
			}

			if (resources != null) {
				for (AnnotatedMethod annotated : methods.values()) {
					Method method = annotated.getMethod();
					int modifiers = method.getModifiers();

					if ((instance != null) || Modifier.isStatic(modifiers)) {
						InvocableMethod invocable = annotated.getInvocableMethod();
						ExtensionFunction function = annotated.getExtensionFunction();
						SubstitutableMethod substitutable = annotated.getSubstitutableMethod();

						if (function != null) {
							if ((invocable != null) || (substitutable != null)) {
								Trace.error(String.format("method '%s' : ExtensionFunction can't be combined with Invocable or Substitutable", method.getName()));
							} else {
								String resourceName = name(method, function);
								ContextResource resource = createFunctionResource(instance, clazz, annotated, resourceName, filterName);

								registerResource(resources, duplicates, resourceName, resource);
							}
						} else {
							Class<?> returnType = method.getReturnType();

							if (invocable != null) {
								if (boolean.class.equals(returnType) || Boolean.class.equals(returnType)) {
									String invocableName = name(method, invocable);
									ContextResource resource = null;

									if (substitutable != null) {
										String substitutableName = name(method, substitutable);

										if (invocableName.equals(substitutableName)) {
											/*
											 * special case Invocable or Substitutable returning boolean with same name
											 * on same method
											 */
											resource = new ReflectedSubstitutableBooleanResource(instance, clazz, annotated, invocableName, filterName);
											substitutable = null;
										}
									}

									if (resource == null) {
										resource = createInvocableResource(instance, clazz, annotated, invocableName, filterName);
									}

									registerResource(resources, duplicates, invocableName, resource);
								} else {
									Trace.error(String.format("method '%s' must return a boolean to be invocable", method.getName()));
								}
							}

							if (substitutable != null) {
								String substitutableName = name(method, substitutable);
								ContextResource resource = createSubstitutableResource(instance, clazz, annotated, returnType, substitutableName, filterName);

								registerResource(resources, duplicates, substitutableName, resource);
							}
						}
					} else {
						Trace.error(String.format("method '%s' must be static (no instance provided)", method.getName()));
					}
				}
			}
		}
	}

	/**
	 * register a resource in the resource map, checking duplicates.
	 * 
	 * @param resources    resources map which will host the new resource
	 * @param duplicates   duplicate names for reflected methods
	 * @param resourceName name of resource to be registered
	 * @param resource     resource instance
	 */
	private static void registerResource(Map<String, ContextResource> resources, Set<String> duplicates, String resourceName, ContextResource resource) {
		if (duplicates.contains(resourceName)) {
			Trace.error(String.format("resource '%s' is declared more than once", resourceName));
		} else {
			resources.put(resourceName, resource);
		}
	}

	/**
	 * record an annotated method and check for duplicate names
	 * 
	 * @param exported     set of exported names
	 * @param duplicates   set of duplicate names
	 * @param method       method to be added
	 * @param annotation   method annotation
	 * @param nameFunction function to retrieve resource name from annotation and
	 *                     method
	 * 
	 * @param <A>          annotation type parameter. Used to link the name function
	 */
	private static <A> void addAnnotatedMethod(Set<String> exported, Set<String> duplicates, Method method, A annotation, BiFunction<Method, A, String> nameFunction) {
		if (annotation != null) {
			String name = nameFunction.apply(method, annotation);

			if (!exported.add(name)) {
				duplicates.add(name);
			}
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
	private static void scanAnnotatedMethod(Class<?> clazz, Map<Method, AnnotatedMethod> methods) {
		Set<Class<?>> seen = new HashSet<Class<?>>();

		scanAnnotatedMethod(clazz, clazz, methods, seen);
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
	private static void scanAnnotatedMethod(Class<?> base, Class<?> clazz, Map<Method, AnnotatedMethod> methods, Set<Class<?>> seen) {
		if (seen.add(clazz)) {
			Class<?> superClazz = clazz.getSuperclass();
			Class<?>[] interfaces = clazz.getInterfaces();

			if (superClazz != null) {
				scanAnnotatedMethod(base, superClazz, methods, seen);
			}

			for (Class<?> impl : interfaces) {
				scanAnnotatedMethod(base, impl, methods, seen);
			}

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
	public static void reflect(Map<String, ContextResource> resources, Script script, String filterName) {
		Class<? extends Script> clazz = script.getClass();

		reflect(resources, script, clazz, filterName);
	}

	/**
	 * entry to register resources from an object instance
	 * 
	 * @param resources script attached resources
	 * @param instance  extension to be bound to the script
	 */
	public static void reflectInstance(Map<String, ContextResource> resources, Object instance) {
		Class<?> clazz = instance.getClass();

		reflect(resources, instance, clazz, null);
	}

	public static void reflectClass(Map<String, ContextResource> resources, Class<?> clazz) {
		reflect(resources, null, clazz, null);
	}

	/**
	 * creates an invocable resource from the given parameters
	 * 
	 * @param instance   reflected instance if any (maybe <code>null</code>)
	 * @param clazz      reflected class definition
	 * @param annotated  annotated method (maybe static)
	 * @param name       resource name
	 * @param filterName script filter name if applicable (maybe <code>null</code>)
	 * @return a new invocable resource
	 */
	private static InvocableResource createInvocableResource(final Object instance, final Class<?> clazz, final AnnotatedMethod annotated, final String name, final String filterName) {
		return new ReflectedInvocableResource(instance, clazz, annotated, name, filterName);
	}

	/**
	 * creates an function resource from the given parameters
	 * 
	 * @param instance   reflected instance if any (maybe <code>null</code>)
	 * @param clazz      reflected class definition
	 * @param annotated  annotated method (maybe static)
	 * @param name       resource name
	 * @param filterName script filter name if applicable (maybe <code>null</code>)
	 * @return a new function resource
	 */
	private static FunctionResource createFunctionResource(final Object instance, final Class<?> clazz, final AnnotatedMethod annotated, final String name, final String filterName) {
		/* first argument of functions is the context dictionary, can be a Message */
		final Method method = annotated.getMethod();
		final Class<?> dictionaryType = getFunctionDictionaryType(method);

		if (dictionaryType == null) {
			if (filterName != null) {
				Trace.error(String.format("first argument of '%s' in script '%s' must be a dictionary", method.getName(), filterName));
			} else {
				Trace.error(String.format("first argument of '%s' must be a dictionary", method));
			}

			return null;
		}

		return new FunctionResource() {
			@Override
			public Object invoke(Dictionary dict, Object... args) throws CircuitAbortException {
				try {
					/* update arguments and prepend message/dictionary */
					Message msg = dict instanceof Message ? (Message) dict : null;
					Object[] params = ContextResourceResolver.coerceFunctionArguments(method, setupParams(msg, dict, dictionaryType, args));

					if (Trace.isDebugEnabled()) {
						if (filterName != null) {
							Trace.debug(String.format("call '%s' from script '%s' (bound as '%s')", method.getName(), filterName, name));
						} else {
							Trace.debug(String.format("call '%s' (bound as '%s')", method, name));
						}
					}

					Object result = method.invoke(instance, params);

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
	private static Object[] setupParams(Message msg, Dictionary dict, Class<?> dictType, Object[] args) {
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

	/**
	 * creates an substitutable resource from the given parameters
	 * 
	 * @param <T>        type parameter locked to method return type
	 * @param instance   reflected instance if any (maybe <code>null</code>)
	 * @param clazz      reflected class definition
	 * @param annotated  annotated method (maybe static)
	 * @param returnType method return type
	 * @param name       resource name
	 * @param filterName script filter name if applicable (maybe <code>null</code>)
	 * @return a new substitutable resource
	 */
	private static <T> SubstitutableResource<T> createSubstitutableResource(final Object instance, final Class<?> clazz, final AnnotatedMethod annotated, final Class<T> returnType, String name, final String filterName) {
		return new ReflectedSubstitutableResource<T>(instance, clazz, annotated, returnType, name, filterName);
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

	private static class ReflectedResource<T> {
		private final InjectableParameter<?>[] parameters;
		private final Method method;
		private final Object instance;
		private final String name;
		private final String filterName;
		private final Class<T> returnType;

		private ReflectedResource(Object instance, AnnotatedMethod annotated, Class<T> returnType, String name, String filterName) {
			this.parameters = processInjectableParameters(annotated);
			this.method = annotated.getMethod();

			this.instance = instance;
			this.name = name;
			this.filterName = filterName;
			this.returnType = returnType;
		}

		protected void debugInvoke(Method method, String[] debug, String name, String filterName) {
			Trace.debug(String.format("provided parameters : '%s'", debugInjectableParameters(debug)));
		}

		protected T invokeMethod(Dictionary dict) throws InvocationTargetException {
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
					debugInvoke(method, debug, name, filterName);
				}

				Object value = method.invoke(instance, resolved);

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
				throw new IllegalStateException("method is not accessible", e);
			}

			return result;
		}
	}

	private static class ReflectedInvocableResource extends ReflectedResource<Boolean> implements InvocableResource {
		private ReflectedInvocableResource(Object instance, Class<?> clazz, AnnotatedMethod annotated, String name, String filterName) {
			super(instance, annotated, Boolean.class, name, filterName);
		}

		@Override
		protected void debugInvoke(Method method, String[] debug, String name, String filterName) {
			if (filterName != null) {
				Trace.debug(String.format("invoke '%s' from script '%s' (bound as '%s')", method.getName(), filterName, name));
			} else {
				Trace.debug(String.format("invoke '%s' (bound as '%s')", method, name));
			}

			super.debugInvoke(method, debug, name, filterName);
		}

		@Override
		public boolean invoke(Message m) throws CircuitAbortException {
			try {
				Boolean result = super.invokeMethod(m);

				if (result == null) {
					throw new CircuitAbortException("method did not return a boolean");
				}

				return result;
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
	}

	private static class ReflectedSubstitutableResource<T> extends ReflectedResource<T> implements SubstitutableResource<T> {
		private ReflectedSubstitutableResource(Object instance, Class<?> clazz, AnnotatedMethod annotated, Class<T> returnType, String name, String filterName) {
			super(instance, annotated, returnType, name, filterName);
		}

		@Override
		protected void debugInvoke(Method method, String[] debug, String name, String filterName) {
			if (filterName != null) {
				Trace.debug(String.format("substitute '%s' from script '%s' (bound as '%s')", method.getName(), filterName, name));
			} else {
				Trace.debug(String.format("substitute '%s' (bound as '%s')", method, name));
			}

			super.debugInvoke(method, debug, name, filterName);
		}

		@Override
		public T substitute(Dictionary dict) {
			try {
				return super.invokeMethod(dict);
			} catch (InvocationTargetException e) {
				Throwable cause = e.getCause();

				/*
				 * since exceptions are silenced for substitutables, do not report an error but
				 * show the stacktrace in debug
				 */
				Trace.debug("got error when invoking method", cause);
			} catch (RuntimeException e) {
				/*
				 * since exceptions are silenced for substitutables, do not report an error but
				 * show the stacktrace in debug
				 */
				Trace.debug("unexpected exception", e);
			}

			return null;
		}
	}

	private static class ReflectedSubstitutableBooleanResource extends ReflectedInvocableResource implements SubstitutableResource<Boolean> {
		private ReflectedSubstitutableBooleanResource(Object instance, Class<?> clazz, AnnotatedMethod annotated, String name, String filterName) {
			super(instance, clazz, annotated, name, filterName);
		}

		@Override
		public Boolean substitute(Dictionary dict) {
			try {
				return super.invokeMethod(dict);
			} catch (InvocationTargetException e) {
				Throwable cause = e.getCause();

				/*
				 * since exceptions are silenced for substitutables, do not report an error but
				 * show the stacktrace in debug
				 */
				Trace.debug("got error when invoking method", cause);
			} catch (RuntimeException e) {
				/*
				 * since exceptions are silenced for substitutables, do not report an error but
				 * show the stacktrace in debug
				 */
				Trace.debug("unexpected exception", e);
			}

			return null;
		}
	}
}
