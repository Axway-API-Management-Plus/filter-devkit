package com.vordel.circuit.script.context;

import java.util.Collections;
import java.util.Map;

import com.vordel.circuit.script.bind.ExtensionContext;
import com.vordel.circuit.script.context.resources.ContextResource;
import com.vordel.circuit.script.context.resources.ContextResourceProvider;

/**
 * This class implements a {@link ScriptInvocationContext} which is exportable
 * into a message attribute. The processor and circuit will be updated by a
 * message listener according to the current filter being executed.
 * 
 * @author rdesaintleger@axway.com
 */
public class MessageInvocationContext extends AbstractInvocationContext {
	/**
	 * Bound resources
	 */
	private final Map<String, ContextResource> resources;

	private final ContextResourceProvider parent;

	/**
	 * Builds a message invocation context, register a message listener for update
	 * and sets the according message attribut with the newly created instance
	 * 
	 * @param resources context resource map (if null, an empty map is used).
	 * @param parent    parent context resource map (for inheritance)
	 */
	protected MessageInvocationContext(Map<String, ContextResource> resources, ContextResourceProvider parent) {
		if (resources == null) {
			resources = Collections.emptyMap();
		}

		this.resources = resources;
		this.parent = parent;
	}

	public static ScriptInvocationContext createScriptInvocationContext() {
		return createScriptInvocationContext(null);
	}

	public static ScriptInvocationContext createScriptInvocationContext(Map<String, ContextResource> resources) {
		return createScriptInvocationContext(resources, null);
	}

	public static ScriptInvocationContext createScriptInvocationContext(Map<String, ContextResource> resources, ContextResourceProvider parent) {
		return new MessageInvocationContext(resources, parent);
	}

	public static ScriptInvocationContext asScriptInvocationContext(ExtensionContext context) {
		return createScriptInvocationContext(context.getContextResources(), null);
	}

	public static ScriptInvocationContext asScriptInvocationContext(ExtensionContext context, ContextResourceProvider parent) {
		return createScriptInvocationContext(context.getContextResources(), parent);
	}

	@Override
	public ContextResource getContextResource(String name) {
		ContextResource resource = resources.get(name);

		if ((resource == null) && (parent != null)) {
			resource = parent.getContextResource(name);
		}

		return resource;
	}
}
