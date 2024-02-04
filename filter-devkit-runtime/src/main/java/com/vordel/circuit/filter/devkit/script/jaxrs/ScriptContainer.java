package com.vordel.circuit.filter.devkit.script.jaxrs;

import org.glassfish.jersey.server.ResourceConfig;

import com.vordel.circuit.filter.devkit.jaxrs.AbstractContainer;

public class ScriptContainer<T> extends AbstractContainer<ScriptWebComponent> {
	private ScriptWebComponent component = null;
	private final T script;

	public ScriptContainer(T script) {
		this.script = script;
	}

	public final T getScriptResource() {
		return script;
	}

	public final ScriptWebComponent getWebComponent() {
		return component;
	}

	@Override
	public final void reload(ResourceConfig configuration) {
		if (configuration != null) {
			this.component = new ScriptWebComponent(configuration, getScriptResource());
		} else {
			this.component = null;
		}
	}
}
