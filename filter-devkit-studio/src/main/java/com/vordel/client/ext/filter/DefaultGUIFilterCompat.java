package com.vordel.client.ext.filter;

import java.lang.reflect.Field;

import com.vordel.client.manager.filter.DefaultGUIFilter;
import com.vordel.common.ResourceBase;

public class DefaultGUIFilterCompat extends DefaultGUIFilter {
	private final Field resourceBase;
	private final Field resourceBaseLocal;

	public DefaultGUIFilterCompat() {
		super();

		/* retrieve resource base as reflected fields */
		this.resourceBase = reflectField(DefaultGUIFilter.class, "resourceBase");
		this.resourceBaseLocal = reflectField(DefaultGUIFilter.class, "resourceBaseLocal");
	}
	
	protected static final Field reflectField(Class<?> clazz, String name) {
		try {
			Field field = clazz.getDeclaredField(name);
			
			field.setAccessible(true);
			
			return field;
		} catch (NoSuchFieldException e) {
			throw new IllegalStateException("unable to locate fields", e);
		}
	}
	
	private ResourceBase getResourceBase() {
		try {
			return (ResourceBase) resourceBase.get(this);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}
	
	private ResourceBase getResourceBaseLocal() {
		try {
			return (ResourceBase) resourceBaseLocal.get(this);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	public String resolve(String name) {
		try {
			return getResourceBase().getResourceString(name);
		} catch (Exception e) {
			return getResourceBaseLocal().getResourceString(name);
		}
	}

	public String resolve(String name, Object... p1) {
		try {
			return getResourceBase().getResourceString(name, p1);
		} catch (Exception e) {
			return getResourceBaseLocal().getResourceString(name, p1);
		}
	}
}
