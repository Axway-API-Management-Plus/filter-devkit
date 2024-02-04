package com.vordel.circuit.filter.devkit.quick;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.filter.devkit.context.ExtensionLoader;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterComponent;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterField;
import com.vordel.client.manager.filter.devkit.runtime.QuickJavaFilter;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;
import com.vordel.es.EntityType;
import com.vordel.trace.Trace;

public class QuickJavaFilterProcessor extends MessageProcessor {
	private QuickJavaFilterDefinition instance = null;

	@Override
	public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
		super.filterAttached(ctx, entity);

		String definitionName = entity.getType().getName();
		Class<?> definitionClazz = ExtensionLoader.getQuickJavaFilter(definitionName);

		if (definitionClazz == null) {
			throw new EntityStoreException(String.format("Missing class for filter type '%s'", definitionName));
		}

		try {
			Set<String> duplicates = new HashSet<String>();
			Constructor<?> contructor = definitionClazz.getDeclaredConstructor();

			Map<Method, QuickFilterComponent> components = QuickJavaFilter.scanComponents(definitionClazz, duplicates);

			for(String duplicate : duplicates) {
				Trace.error(String.format("got duplicate component '%s' declaration for filter '%s' (class '%s')", duplicate, definitionName, definitionClazz.getName()));
			}
			
			Map<Method, QuickFilterField> fields = QuickJavaFilter.scanFields(definitionClazz, duplicates);

			for(String duplicate : duplicates) {
				Trace.error(String.format("got duplicate field '%s' declaration for filter '%s' (class '%s')", duplicate, definitionName, definitionClazz.getName()));
			}
			
			try {
				contructor.setAccessible(true);
				instance = filterAttached(ctx, entity, QuickJavaFilterDefinition.class.cast(contructor.newInstance()), fields, components);
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
	
	private static QuickJavaFilterDefinition filterAttached(ConfigContext ctx, Entity entity, QuickJavaFilterDefinition instance, Map<Method, QuickFilterField> fields, Map<Method, QuickFilterComponent> components) {
		Class<?> definitionClazz = instance.getClass();
		EntityStore es = ctx.getStore();

		for(Entry<Method, QuickFilterComponent> entry : components.entrySet()) {
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
				Trace.error(String.format("got error setting component '%s' parameter for class '%s'", component.name(), definitionClazz.getName()), e);
			} finally {
				method.setAccessible(false);
			}
		}

		for(Entry<Method, QuickFilterField> entry : fields.entrySet()) {
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

		return instance.invokeFilter(c, m, this);
	}
}
