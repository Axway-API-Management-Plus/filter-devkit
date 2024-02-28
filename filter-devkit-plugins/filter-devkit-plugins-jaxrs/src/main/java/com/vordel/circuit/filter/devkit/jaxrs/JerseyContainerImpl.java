package com.vordel.circuit.filter.devkit.jaxrs;

import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Provider;

import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.inject.ReferencingFactory;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.process.internal.RequestScoped;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spi.RequestScopedInitializer;

import com.vordel.circuit.Message;
public class JerseyContainerImpl extends JerseyContainer {
	@Override
	public RequestScopedInitializer getRequestScopedInitializer(Message message) {
		return new RequestScopedInitializer() {
			@Override
			public void initialize(InjectionManager manager) {
				manager.<Ref<Message>>getInstance(MESSAGE_TYPE.getType()).set(message);
				
				/* create web resource context */
				manager.createAndInitialize(WebResourceContext.class);
			}
		};
	}

	@Override
	public ApplicationHandler createApplicationHandler(ResourceConfig configuration) {
		WebComponentBinder binder = new WebComponentBinder(MessageReferencingFactory.class);

		return new ApplicationHandler(configuration, binder);
	}

	private static class WebComponentBinder extends AbstractBinder {
		private final Class<? extends Supplier<Message>> messageSupplier;

		public WebComponentBinder(Class<? extends Supplier<Message>> messageSupplier) {
			this.messageSupplier = messageSupplier;
		}

		@Override
		protected void configure() {
			// current message
			bindFactory(messageSupplier).to(Message.class).proxy(false).proxyForSameScope(false).in(RequestScoped.class);
			bindFactory(ReferencingFactory.referenceFactory()).to(MESSAGE_TYPE).in(RequestScoped.class);
		}
	}

	private static class MessageReferencingFactory extends ReferencingFactory<Message> {
		@Inject
		public MessageReferencingFactory(final Provider<Ref<Message>> referenceFactory) {
			super(referenceFactory);
		}
	}
}
