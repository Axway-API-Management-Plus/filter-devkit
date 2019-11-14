package com.vordel.circuit.ext.filter.quick;

import java.lang.reflect.Method;
import java.util.ArrayList;
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

	public static Map<Method, QuickFilterField> scanFields(Class<?> clazz, Set<String> duplicates) {
		List<Method> annotated = scanFields(clazz, new ArrayList<Method>());
		Map<Method, QuickFilterField> methods = new HashMap<Method, QuickFilterField>();

		for(Method method : annotated) {
			Class<?>[] parameters = method.getParameterTypes();

			/* filter methods that can be set by runtime */
			if ((parameters.length == 3) && (ConfigContext.class.equals(parameters[0])) && (Entity.class.equals(parameters[1])) && (String.class.equals(parameters[2]))) {
				QuickFilterField field = method.getAnnotation(QuickFilterField.class);

				try {
					/* find a better method if possible */
					method = clazz.getMethod(method.getName(), method.getParameterTypes());
				} catch (NoSuchMethodException e) {
				}

				/* inheritance may discard field declaration */
				methods.put(method, field);
			}
		}

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

	private static List<Method> scanFields(Class<?> clazz, List<Method> annotated) {
		Class<?> superClazz = clazz.getSuperclass();
		Class<?>[] interfaces = clazz.getInterfaces();

		if (superClazz != null) {
			annotated = scanFields(superClazz, annotated);
		}

		for(Class<?> impl : interfaces) {
			annotated = scanFields(impl, annotated);
		}

		for(Method method : clazz.getDeclaredMethods()) {
			QuickFilterField field = method.getAnnotation(QuickFilterField.class);

			if (field != null) {
				annotated.add(method);
			}
		}

		return annotated;
	}
}
