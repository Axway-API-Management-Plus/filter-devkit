package com.vordel.circuit.filter.devkit.script.jaxrs;

import java.lang.reflect.Method;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.core.Application;

import org.glassfish.jersey.internal.inject.ReferencingFactory;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.spi.RequestScopedInitializer;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.jaxrs.AbstractWebComponent;

public class ScriptWebComponent extends AbstractWebComponent {
	protected static final RequestScopedInitializer getRequestScopedInitializer(Message message) {
		return CONTAINER.getRequestScopedInitializer(message);
	}

	protected static class MessageReferencingFactory extends ReferencingFactory<Message> {
		@Inject
		public MessageReferencingFactory(final Provider<Ref<Message>> referenceFactory) {
			super(referenceFactory);
		}
	}

	public static final class Builder extends AbstractWebComponent.Builder {
		protected Builder() {
		}

		public <T> ScriptWebComponent build(T script) {
			ScriptWebComponent component = null;

			/* check if we are called from policy studio */
			if ((script != null) && buildable()) {
				ScriptContainer<T> container = new ScriptContainer<T>(script);
				Application application = new VordelApplication(classes, singletons, properties);

				container.reload(application);
				component = container.getWebComponent();
			}

			return component;
		}
	}

	protected static Resource createScriptResource(Object script) {
		Resource resource = Resource.from(script.getClass(), false);

		/* adds a path to the root resource before returning it */
		return createScriptResource(resource, script).path("").build();
	}

	private static Resource.Builder createScriptResource(Resource resource, Object script) {
		Resource.Builder builder = Resource.builder();
		Class<?> clazz = script.getClass();

		builder.name(resource.getName()).path(resource.getPath()).extended(resource.isExtended());

		for (ResourceMethod method : resource.getAllMethods()) {
			Invocable invocable = method.getInvocable();
			Method handler = invocable.getHandlingMethod();

			if (handler == null) {
				handler = invocable.getDefinitionMethod();
			}

			if ((handler != null) && handler.getDeclaringClass().isAssignableFrom(clazz)) {
				/* sets the script as an handler for this method */
				builder.addMethod(method).handledBy(script, handler);
			}
		}

		for (Resource child : resource.getChildResources()) {
			/* recursively update child resources */
			builder.addChildResource(createScriptResource(child, script).build());
		}

		return builder;
	}

	protected ScriptWebComponent(ResourceConfig configuration, Object root) {
		super(configuration.registerResources(createScriptResource(root)));
	}

	@Override
	protected ApplicationHandler createApplicationHandler(ResourceConfig configuration) {
		return CONTAINER.createApplicationHandler(configuration);
	}

	public static final <T> ScriptWebComponent createWebComponent(T script) {
		return builder().build(script);
	}

	public static final Builder builder() {
		return new Builder();
	}

	protected final boolean service(Message message, boolean reportNoMatch) throws CircuitAbortException {
		return service(getRequestScopedInitializer(message), message, reportNoMatch);
	}

	public final boolean service(Message message) throws CircuitAbortException {
		return service(message, false);
	}

	public final boolean filter(Message message) throws CircuitAbortException {
		return service(message, true);
	}
}
