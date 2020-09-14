package com.vordel.circuit.jaxrs;

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
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.script.context.MessageContextTracker;
import com.vordel.config.Circuit;

public class Jersey231Container extends JerseyContainer {
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
		private final Class<? extends Supplier<MessageProcessor>> processorSupplier;
		private final Class<? extends Supplier<MessageContextTracker>> contextTrackerSupplier;
		private final Class<? extends Supplier<Message>> messageSupplier;
		private final Class<? extends Supplier<Circuit>> circuitSupplier;

		public WebComponentBinder(Class<? extends Supplier<Message>> messageSupplier) {
			this(MessageProcessorReferencingFactory.class, MessageContextTrackerReferencingFactory.class, messageSupplier, CircuitReferencingFactory.class);
		}

		public WebComponentBinder(Class<? extends Supplier<MessageProcessor>> processorSupplier, Class<? extends Supplier<MessageContextTracker>> contextTrackerSupplier, Class<? extends Supplier<Message>> messageSupplier, Class<? extends Supplier<Circuit>> circuitSupplier) {
			this.processorSupplier = processorSupplier;
			this.contextTrackerSupplier = contextTrackerSupplier;
			this.messageSupplier = messageSupplier;
			this.circuitSupplier = circuitSupplier;
		}

		@Override
		protected void configure() {
			bindFactory(contextTrackerSupplier).to(MessageContextTracker.class).proxy(false).proxyForSameScope(false).in(RequestScoped.class);
			bindFactory(ReferencingFactory.referenceFactory()).to(MESSAGEPCONTEXTTRACKER_TYPE).in(RequestScoped.class);

			// current message processor
			bindFactory(processorSupplier).to(MessageProcessor.class).proxy(false).proxyForSameScope(false).in(RequestScoped.class);
			bindFactory(ReferencingFactory.referenceFactory()).to(MESSAGEPROCESSOR_TYPE).in(RequestScoped.class);

			// current message
			bindFactory(messageSupplier).to(Message.class).proxy(false).proxyForSameScope(false).in(RequestScoped.class);
			bindFactory(ReferencingFactory.referenceFactory()).to(MESSAGE_TYPE).in(RequestScoped.class);

			// current circuit
			bindFactory(circuitSupplier).to(Circuit.class).proxy(false).proxyForSameScope(false).in(RequestScoped.class);
			bindFactory(ReferencingFactory.referenceFactory()).to(CIRCUIT_TYPE).in(RequestScoped.class);
		}
	}

	private static class MessageReferencingFactory extends ReferencingFactory<Message> {
		@Inject
		public MessageReferencingFactory(final Provider<Ref<Message>> referenceFactory) {
			super(referenceFactory);
		}
	}

	private static class MessageContextTrackerReferencingFactory implements Supplier<MessageContextTracker> {
		private final Provider<Ref<Message>> referenceFactory;

		@Inject
		public MessageContextTrackerReferencingFactory(final Provider<Ref<Message>> referenceFactory) {
			this.referenceFactory = referenceFactory;
		}

		@Override
		public MessageContextTracker get() {
			Message message = referenceFactory.get().get();

			return MessageContextTracker.getMessageContextTracker(message);
		}
	}

	private static class MessageProcessorReferencingFactory implements Supplier<MessageProcessor> {
		private final Provider<Ref<Message>> referenceFactory;

		@Inject
		public MessageProcessorReferencingFactory(final Provider<Ref<Message>> referenceFactory) {
			this.referenceFactory = referenceFactory;
		}

		@Override
		public MessageProcessor get() {
			Message message = referenceFactory.get().get();
			MessageContextTracker tracker = MessageContextTracker.getMessageContextTracker(message);

			return tracker == null ? null : tracker.getMessageProcessor();
		}
	}

	private static class CircuitReferencingFactory implements Supplier<Circuit> {
		private final Provider<Ref<Message>> referenceFactory;

		@Inject
		public CircuitReferencingFactory(final Provider<Ref<Message>> referenceFactory) {
			this.referenceFactory = referenceFactory;
		}

		@Override
		public Circuit get() {
			Message message = referenceFactory.get().get();
			MessageContextTracker tracker = MessageContextTracker.getMessageContextTracker(message);

			return tracker == null ? null : tracker.getCircuit();
		}
	}
}
