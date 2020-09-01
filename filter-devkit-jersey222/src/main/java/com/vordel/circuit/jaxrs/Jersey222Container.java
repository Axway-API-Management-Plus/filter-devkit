package com.vordel.circuit.jaxrs;

import javax.inject.Inject;
import javax.inject.Provider;

import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.internal.inject.ReferencingFactory;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.process.internal.RequestScoped;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spi.RequestScopedInitializer;

import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.script.context.MessageContextTracker;
import com.vordel.config.Circuit;

public class Jersey222Container extends JerseyContainer {
	@Override
	public RequestScopedInitializer getRequestScopedInitializer(Message message) {
		return new RequestScopedInitializer() {
			@Override
			public void initialize(final ServiceLocator locator) {
				locator.<Ref<Message>>getService(MESSAGE_TYPE.getType()).set(message);

				/* create web resource context */
				locator.createAndInitialize(WebResourceContext.class);
			}
		};
	}

	@Override
	public ApplicationHandler createApplicationHandler(ResourceConfig configuration) {
		WebComponentBinder binder = new WebComponentBinder(MessageReferencingFactory.class);

		return new ApplicationHandler(configuration, binder);
	}

	private static class WebComponentBinder extends AbstractBinder {
		private final Class<? extends Factory<MessageProcessor>> processorFactory;
		private final Class<? extends Factory<MessageContextTracker>> contextTrackerFactory;
		private final Class<? extends Factory<Message>> messageFactory;
		private final Class<? extends Factory<Circuit>> circuitFactory;

		public WebComponentBinder(Class<? extends Factory<Message>> messageFactory) {
			this(MessageProcessorReferencingFactory.class, MessageContextTrackerReferencingFactory.class, messageFactory, CircuitReferencingFactory.class);
		}

		public WebComponentBinder(Class<? extends Factory<MessageProcessor>> processorFactory, Class<? extends Factory<MessageContextTracker>> contextTrackerFactory, Class<? extends Factory<Message>> messageFactory, Class<? extends Factory<Circuit>> circuitFactory) {
			this.processorFactory = processorFactory;
			this.contextTrackerFactory = contextTrackerFactory;
			this.messageFactory = messageFactory;
			this.circuitFactory = circuitFactory;
		}

		@Override
		protected void configure() {
			bindFactory(contextTrackerFactory).to(MessageContextTracker.class).proxy(false).proxyForSameScope(false).in(RequestScoped.class);
			bindFactory(ReferencingFactory.<MessageContextTracker>referenceFactory()).to(MESSAGEPCONTEXTTRACKER_TYPE.getType()).in(RequestScoped.class);

			// current message processor
			bindFactory(processorFactory).to(MessageProcessor.class).proxy(false).proxyForSameScope(false).in(RequestScoped.class);
			bindFactory(ReferencingFactory.<MessageProcessor>referenceFactory()).to(MESSAGEPROCESSOR_TYPE.getType()).in(RequestScoped.class);

			// current message
			bindFactory(messageFactory).to(Message.class).proxy(false).proxyForSameScope(false).in(RequestScoped.class);
			bindFactory(ReferencingFactory.<Message>referenceFactory()).to(MESSAGE_TYPE.getType()).in(RequestScoped.class);

			// current circuit
			bindFactory(circuitFactory).to(Circuit.class).proxy(false).proxyForSameScope(false).in(RequestScoped.class);
			bindFactory(ReferencingFactory.<Circuit>referenceFactory()).to(CIRCUIT_TYPE.getType()).in(RequestScoped.class);
		}
	}

	private static class MessageReferencingFactory extends ReferencingFactory<Message> {
		@Inject
		public MessageReferencingFactory(final Provider<Ref<Message>> referenceFactory) {
			super(referenceFactory);
		}
	}

	private static class MessageContextTrackerReferencingFactory implements Factory<MessageContextTracker> {
		private final Provider<Ref<Message>> referenceFactory;

		@Inject
		public MessageContextTrackerReferencingFactory(final Provider<Ref<Message>> referenceFactory) {
			this.referenceFactory = referenceFactory;
		}

		@Override
		public MessageContextTracker provide() {
			Message message = referenceFactory.get().get();

			return MessageContextTracker.getMessageContextTracker(message);
		}

		@Override
		public void dispose(MessageContextTracker instance) {
			// nothing
		}
	}

	private static class MessageProcessorReferencingFactory implements Factory<MessageProcessor> {
		private final Provider<Ref<Message>> referenceFactory;

		@Inject
		public MessageProcessorReferencingFactory(final Provider<Ref<Message>> referenceFactory) {
			this.referenceFactory = referenceFactory;
		}

		@Override
		public MessageProcessor provide() {
			Message message = referenceFactory.get().get();
			MessageContextTracker tracker = MessageContextTracker.getMessageContextTracker(message);

			return tracker == null ? null : tracker.getMessageProcessor();
		}

		@Override
		public void dispose(MessageProcessor instance) {
			// nothing
		}
	}

	private static class CircuitReferencingFactory implements Factory<Circuit> {
		private final Provider<Ref<Message>> referenceFactory;

		@Inject
		public CircuitReferencingFactory(final Provider<Ref<Message>> referenceFactory) {
			this.referenceFactory = referenceFactory;
		}

		@Override
		public Circuit provide() {
			Message message = referenceFactory.get().get();
			MessageContextTracker tracker = MessageContextTracker.getMessageContextTracker(message);

			return tracker == null ? null : tracker.getCircuit();
		}

		@Override
		public void dispose(Circuit instance) {
			// nothing
		}
	}
}
