package com.vordel.circuit.filter.devkit.script.jaxrs;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.glassfish.jersey.internal.PropertiesDelegate;

public class MessagePropertiesDelegate implements PropertiesDelegate {
	private final Map<String, Object> store;

	/**
	 * Create new map-based properties delegate.
	 */
	public MessagePropertiesDelegate() {
		this(new HashMap<String, Object>());
	}

	/**
	 * Create new map-based properties delegate.
	 *
	 * @param store backing property store.
	 */
	public MessagePropertiesDelegate(Map<String, Object> store) {
		this.store = store;
	}

	@Override
	public Object getProperty(String name) {
		return store.get(name);
	}

	@Override
	public Collection<String> getPropertyNames() {
		return Collections.unmodifiableCollection(store.keySet());
	}

	@Override
	public void setProperty(String name, Object value) {
		store.put(name, value);
	}

	@Override
	public void removeProperty(String name) {
		store.remove(name);
	}
}
