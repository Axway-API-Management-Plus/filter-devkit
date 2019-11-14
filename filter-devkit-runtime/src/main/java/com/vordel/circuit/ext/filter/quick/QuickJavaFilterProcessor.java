package com.vordel.circuit.ext.filter.quick;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.script.context.MessageContextModule;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;
import com.vordel.trace.Trace;

public class QuickJavaFilterProcessor extends MessageProcessor {
	private QuickJavaFilterDefinition instance = null;

	@Override
	public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
		super.filterAttached(ctx, entity);

		String definitionName = entity.getType().getName();
		Class<?> definitionClazz = MessageContextModule.getQuickJavaFilter(definitionName);

		if (definitionClazz == null) {
			throw new EntityStoreException(String.format("Missing class for filter type '%s'", definitionName));
		}
		

		try {
			Set<String> duplicates = new HashSet<String>();
			Map<Method, QuickFilterField> methods = QuickJavaFilter.scanFields(definitionClazz, duplicates);
			Constructor<?> contructor = definitionClazz.getDeclaredConstructor();

			for(String duplicate : duplicates) {
				Trace.error(String.format("got duplicate field '%s' declaration for filter '%s' (class '%s')", duplicate, definitionName, definitionClazz.getName()));
			}
			
			try {
				contructor.setAccessible(true);
				instance = filterAttached(ctx, entity, QuickJavaFilterDefinition.class.cast(contructor.newInstance()), methods);
			} finally {
				contructor.setAccessible(false);
			}
		} catch (InstantiationException e) {
			Trace.error(String.format("the class '%s' can't be instanciated", definitionClazz.getName()), e);
		} catch (IllegalAccessException e) {
			Trace.error(String.format("the no-arg contructor for class '%s' is not accessible", definitionClazz.getName()), e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();

			Trace.error(String.format("got error instanciating class '%s'", definitionClazz.getName()), cause);
		} catch (NoSuchMethodException e) {
			Trace.error(String.format("the class '%s' must have a no-arg contructor", definitionClazz.getName()), e);
		}
	}
	
	private static QuickJavaFilterDefinition filterAttached(ConfigContext ctx, Entity entity, QuickJavaFilterDefinition instance, Map<Method, QuickFilterField> methods) {
		Class<?> definitionClazz = instance.getClass();

		for(Entry<Method, QuickFilterField> entry : methods.entrySet()) {
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
				instance.detachFilter();
			} catch (Exception e) {
				Trace.error("Got error detaching filter", e);
			}

			instance = null;
		}

		super.filterDetached();
	}

	@Override
	public boolean invoke(Circuit c, Message m) throws CircuitAbortException {
		if (instance == null) {
			throw new CircuitAbortException("No underlying implementation");
		}

		return instance.invokeFilter(c, m);
	}
}
