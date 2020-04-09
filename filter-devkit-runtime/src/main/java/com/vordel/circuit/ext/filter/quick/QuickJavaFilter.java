package com.vordel.circuit.ext.filter.quick;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vordel.circuit.MessageProcessor;
import com.vordel.config.ConfigContext;
import com.vordel.es.Entity;

public class QuickJavaFilter extends AbstractQuickFilter {
	@Override
	public Class<? extends MessageProcessor> getMessageProcessorClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.circuit.ext.filter.quick.QuickJavaFilterProcessor").asSubclass(MessageProcessor.class);
	}

	public static Map<Method, QuickFilterComponent> scanComponents(Class<?> clazz, Set<String> duplicates) {
		Map<Method, QuickFilterComponent> methods = scanMethods(clazz, QuickFilterComponent.class, Collection.class);

		/* search for components duplicates */
		Set<String> fieldNames = new HashSet<String>();

		if (duplicates == null) {
			duplicates = new HashSet<String>();
		}

		for(QuickFilterComponent field : methods.values()) {
			String name = field.name();

			if (!fieldNames.add(name)) {
				/* report duplicate components without throwing exception */
				duplicates.add(name);
			}
		}

		/* last step, remove duplicate components */
		Iterator<QuickFilterComponent> iterator = methods.values().iterator();

		while(iterator.hasNext()) {
			QuickFilterComponent field = iterator.next();
			String name = field.name();

			if (duplicates.contains(name)) {
				iterator.remove();
			}
		}

		return methods;
	}

	public static Map<Method, QuickFilterField> scanFields(Class<?> clazz, Set<String> duplicates) {
		Map<Method, QuickFilterField> methods = scanMethods(clazz, QuickFilterField.class, String.class);

		/* search for field duplicates */
		Set<String> fieldNames = new HashSet<String>();

		if (duplicates == null) {
			duplicates = new HashSet<String>();
		}

		for(QuickFilterField field : methods.values()) {
			String name = field.name();

			if (!fieldNames.add(name)) {
				/* report duplicate fields without throwing exception */
				duplicates.add(name);
			}
		}

		/* last step, remove duplicate fields */
		Iterator<QuickFilterField> iterator = methods.values().iterator();

		while(iterator.hasNext()) {
			QuickFilterField field = iterator.next();
			String name = field.name();

			if (duplicates.contains(name)) {
				iterator.remove();
			}
		}

		return methods;
	}
	
	private static <T extends Annotation> Map<Method, T> scanMethods(Class<?> clazz, Class<T> annotation, Class<?> parameter) {
		List<Method> annotated = scanMethods(clazz, new ArrayList<Method>(), annotation);
		Map<Method, T> methods = new HashMap<Method, T>();

		for(Method method : annotated) {
			Class<?>[] parameters = method.getParameterTypes();

			/* filter methods that can be set by runtime */
			if ((parameters.length == 3) && (ConfigContext.class.equals(parameters[0])) && (Entity.class.equals(parameters[1])) && (parameter.equals(parameters[2]))) {
				T field = method.getAnnotation(annotation);

				try {
					/* find a better method if possible */
					method = clazz.getMethod(method.getName(), method.getParameterTypes());
				} catch (NoSuchMethodException e) {
				}

				/* inheritance may discard field declaration */
				methods.put(method, field);
			}
		}

		return methods;
	}

	private static List<Method> scanMethods(Class<?> clazz, List<Method> annotated, Class<?  extends Annotation> annotation) {
		Class<?> superClazz = clazz.getSuperclass();
		Class<?>[] interfaces = clazz.getInterfaces();

		if (superClazz != null) {
			annotated = scanMethods(superClazz, annotated, annotation);
		}

		for(Class<?> impl : interfaces) {
			annotated = scanMethods(impl, annotated, annotation);
		}

		for(Method method : clazz.getDeclaredMethods()) {
			Annotation field = method.getAnnotation(annotation);

			if (field != null) {
				annotated.add(method);
			}
		}

		return annotated;
	}
}
