package com.vordel.circuit.filter.devkit.quick;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterComponent;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterField;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;
import com.vordel.es.EntityType;
import com.vordel.trace.Trace;

/**
 * Used as {@link JavaQuickFilterDefinition} proxy. This class will create and
 * use the definition class. this class is inherited by Quick Filter annotation
 * processor code generator.
 * 
 * @author rdesaintleger@axway.com
 */
public abstract class JavaQuickFilterProcessor extends MessageProcessor {
	/**
	 * This method is implemented by the code generator. Is will return the
	 * definition class object.
	 * 
	 * @return the definition class
	 * @throws ClassNotFoundException if the class could not be found.
	 */
	public abstract Class<? extends JavaQuickFilterDefinition> getQuickFilterDefinition() throws ClassNotFoundException;

	/**
	 * instanciated Quick Filter instance
	 */
	private JavaQuickFilterDefinition instance = null;

	@Override
	public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
		super.filterAttached(ctx, entity);

		EntityType type = entity.getType();
		String name = type.getName();

		Class<?> definitionClazz = null;
		String definitionName = null;

		try {
			/* retrieve the definition class and associated simple name */
			definitionClazz = getQuickFilterDefinition();
			definitionName = definitionClazz.getSimpleName();
		} catch (ClassNotFoundException e) {
			throw new EntityStoreException(String.format("Missing class for filter type '%s'", name));
		}

		try {
			Set<String> duplicates = new HashSet<String>();
			Constructor<?> contructor = definitionClazz.getDeclaredConstructor();

			Map<Method, QuickFilterComponent> components = JavaQuickFilterProcessor.scanComponents(definitionClazz, duplicates);

			for (String duplicate : duplicates) {
				Trace.error(String.format("got duplicate component '%s' declaration for filter '%s' (class '%s')", duplicate, definitionName, definitionClazz.getName()));
			}

			Map<Method, QuickFilterField> fields = JavaQuickFilterProcessor.scanFields(definitionClazz, duplicates);

			for (String duplicate : duplicates) {
				Trace.error(String.format("got duplicate field '%s' declaration for filter '%s' (class '%s')", duplicate, definitionName, definitionClazz.getName()));
			}

			try {
				/* instantiate the definition class... */
				contructor.setAccessible(true);
				
				/* ... and configure it */
				instance = filterAttached(ctx, entity, JavaQuickFilterDefinition.class.cast(contructor.newInstance()), fields, components);
			} finally {
				contructor.setAccessible(false);
			}
		} catch (InstantiationException e) {
			if (Trace.isDebugEnabled()) {
				Trace.error(String.format("the class '%s' can't be instanciated", definitionClazz.getName()), e);
			} else {
				Trace.error(String.format("the class '%s' can't be instanciated", definitionClazz.getName()));
			}

			throw new EntityStoreException(String.format("got error instanciating class for filter type '%s'", name), e);
		} catch (IllegalAccessException e) {
			if (Trace.isDebugEnabled()) {
				Trace.error(String.format("the no-arg contructor for class '%s' is not accessible", definitionClazz.getName()), e);
			} else {
				Trace.error(String.format("the no-arg contructor for class '%s' is not accessible", definitionClazz.getName()));
			}
			throw new EntityStoreException(String.format("got error instanciating class for filter type '%s'", name), e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();

			if (Trace.isDebugEnabled()) {
				Trace.error(String.format("got error instanciating class '%s'", definitionClazz.getName()), cause);
			} else {
				Trace.error(String.format("got error instanciating class '%s'", definitionClazz.getName()));
			}

			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			} else {
				throw new EntityStoreException(String.format("got error instanciating class for filter type '%s'", name), cause);
			}
		} catch (NoSuchMethodException e) {
			if (Trace.isDebugEnabled()) {
				Trace.error(String.format("the class '%s' must have a no-arg contructor", definitionClazz.getName()), e);
			} else {
				Trace.error(String.format("the class '%s' must have a no-arg contructor", definitionClazz.getName()));
			}

			throw new EntityStoreException(String.format("got error instanciating class for filter type '%s'", name), e);
		}
	}

	private static JavaQuickFilterDefinition filterAttached(ConfigContext ctx, Entity entity, JavaQuickFilterDefinition instance, Map<Method, QuickFilterField> fields, Map<Method, QuickFilterComponent> components) {
		Class<?> definitionClazz = instance.getClass();
		EntityStore es = ctx.getStore();

		for (Entry<Method, QuickFilterComponent> entry : components.entrySet()) {
			QuickFilterComponent component = entry.getValue();
			Method method = entry.getKey();

			try {
				method.setAccessible(true);

				Trace.debug(String.format("calling '%s' for component '%s'", method.getName(), component.name()));

				EntityType propertyType = es.getTypeForName(component.name());
				Collection<ESPK> espks = es.listChildren(entity.getPK(), propertyType);

				if (espks == null) {
					espks = Collections.emptySet();
				}

				method.invoke(instance, ctx, entity, espks);
			} catch (IllegalArgumentException e) {
				Trace.error(String.format("got error setting component '%s' parameter for class '%s'", component.name(), definitionClazz.getName()), e);
			} catch (InvocationTargetException e) {
				Throwable cause = e.getCause();

				Trace.error(String.format("got error setting component '%s' parameter for class '%s'", component.name(), definitionClazz.getName()), cause);
			} catch (IllegalAccessException e) {
				/* should not occur since we have set the method accessible */
				Trace.error(String.format("got error setting component '%s' parameter for class '%s'", component.name(), definitionClazz.getName()), e);
			} finally {
				method.setAccessible(false);
			}
		}

		for (Entry<Method, QuickFilterField> entry : fields.entrySet()) {
			QuickFilterField field = entry.getValue();
			Method method = entry.getKey();

			try {
				method.setAccessible(true);

				Trace.debug(String.format("calling '%s' for field '%s'", method.getName(), field.name()));

				method.invoke(instance, ctx, entity, field.name());
			} catch (IllegalArgumentException e) {
				Trace.error(String.format("got error setting field '%s' parameter for class '%s'", field.name(), definitionClazz.getName()), e);
			} catch (InvocationTargetException e) {
				Throwable cause = e.getCause();

				Trace.error(String.format("got error setting field '%s' parameter for class '%s'", field.name(), definitionClazz.getName()), cause);
			} catch (IllegalAccessException e) {
				Trace.error(String.format("got error setting field '%s' parameter for class '%s'", field.name(), definitionClazz.getName()), e);
			} finally {
				method.setAccessible(false);
			}
		}

		/* call master attach method after all fields have been injected */
		instance.attachFilter(ctx, entity);

		return instance;
	}

	@Override
	public void filterDetached() {
		if (instance != null) {
			try {
				/* call the instance detatchment hook */
				instance.detachFilter();
			} catch (Exception e) {
				/* signal any error during detachment */
				Trace.error("Got error detaching filter", e);
			}

			/* help garbage collector */
			instance = null;
		}

		super.filterDetached();
	}

	@Override
	public boolean invoke(Circuit c, Message m) throws CircuitAbortException {
		if (instance == null) {
			throw new CircuitAbortException("No underlying implementation");
		}

		return instance.invokeFilter(c, m, this);
	}

	public static Map<Method, QuickFilterComponent> scanComponents(Class<?> clazz, Set<String> duplicates) {
		Map<Method, QuickFilterComponent> methods = scanMethods(clazz, QuickFilterComponent.class, Collection.class);

		/* search for components duplicates */
		Set<String> fieldNames = new HashSet<String>();

		if (duplicates == null) {
			duplicates = new HashSet<String>();
		}

		for (QuickFilterComponent field : methods.values()) {
			String name = field.name();

			if (!fieldNames.add(name)) {
				/* report duplicate components without throwing exception */
				duplicates.add(name);
			}
		}

		/* last step, remove duplicate components */
		Iterator<QuickFilterComponent> iterator = methods.values().iterator();

		while (iterator.hasNext()) {
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

		for (QuickFilterField field : methods.values()) {
			String name = field.name();

			if (!fieldNames.add(name)) {
				/* report duplicate fields without throwing exception */
				duplicates.add(name);
			}
		}

		/* last step, remove duplicate fields */
		Iterator<QuickFilterField> iterator = methods.values().iterator();

		while (iterator.hasNext()) {
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

		for (Method method : annotated) {
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

	private static List<Method> scanMethods(Class<?> clazz, List<Method> annotated, Class<? extends Annotation> annotation) {
		Class<?> superClazz = clazz.getSuperclass();
		Class<?>[] interfaces = clazz.getInterfaces();

		if (superClazz != null) {
			annotated = scanMethods(superClazz, annotated, annotation);
		}

		for (Class<?> impl : interfaces) {
			annotated = scanMethods(impl, annotated, annotation);
		}

		for (Method method : clazz.getDeclaredMethods()) {
			Annotation field = method.getAnnotation(annotation);

			if (field != null) {
				annotated.add(method);
			}
		}

		return annotated;
	}
}
