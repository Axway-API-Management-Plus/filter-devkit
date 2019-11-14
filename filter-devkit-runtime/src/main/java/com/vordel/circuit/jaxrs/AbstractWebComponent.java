package com.vordel.circuit.jaxrs;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Variant;
import javax.ws.rs.ext.MessageBodyWriter;

import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.internal.inject.ReferencingFactory;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.process.internal.RequestScoped;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerException;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spi.RequestScopedInitializer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.script.context.MessageContextTracker;
import com.vordel.circuit.script.jaxrs.ScriptContainer;
import com.vordel.config.Circuit;
import com.vordel.store.cert.CertStore;
import com.vordel.trace.Trace;

public abstract class AbstractWebComponent {
	protected static final Type MESSAGE_TYPE = (new TypeLiteral<Ref<Message>>() {
	}).getType();
	protected static final Type MESSAGEPCONTEXTTRACKER_TYPE = (new TypeLiteral<Ref<MessageContextTracker>>() {
	}).getType();
	protected static final Type MESSAGEPROCESSOR_TYPE = (new TypeLiteral<Ref<MessageProcessor>>() {
	}).getType();
	protected static final Type CIRCUIT_TYPE = (new TypeLiteral<Ref<Circuit>>() {
	}).getType();

	protected class WebComponentBinder extends AbstractBinder {
		private final Class<? extends Factory<MessageProcessor>> processorFactory;
		private final Class<? extends Factory<MessageContextTracker>> contextTrackerFactory;
		private final Class<? extends Factory<Message>> messageFactory;
		private final Class<? extends Factory<Circuit>> circuitFactory;

		public WebComponentBinder(Class<? extends Factory<Message>> messageFactory) {
			this(MessageProcessorReferencingFactory.class, MessageContextTrackerReferencingFactory.class, messageFactory, CircuitReferencingFactory.class);
		}

		public WebComponentBinder(Class<? extends Factory<MessageProcessor>> processorFactory, Class<? extends Factory<Message>> messageFactory, Class<? extends Factory<Circuit>> circuitFactory) {
			this(processorFactory, MessageContextTrackerReferencingFactory.class, messageFactory, circuitFactory);
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
			bindFactory(ReferencingFactory.<MessageContextTracker>referenceFactory()).to(MESSAGEPCONTEXTTRACKER_TYPE).in(RequestScoped.class);

			// current message processor
			bindFactory(processorFactory).to(MessageProcessor.class).proxy(false).proxyForSameScope(false).in(RequestScoped.class);
			bindFactory(ReferencingFactory.<MessageProcessor>referenceFactory()).to(MESSAGEPROCESSOR_TYPE).in(RequestScoped.class);

			// current message
			bindFactory(messageFactory).to(Message.class).proxy(false).proxyForSameScope(false).in(RequestScoped.class);
			bindFactory(ReferencingFactory.<Message>referenceFactory()).to(MESSAGE_TYPE).in(RequestScoped.class);

			// current circuit
			bindFactory(circuitFactory).to(Circuit.class).proxy(false).proxyForSameScope(false).in(RequestScoped.class);
			bindFactory(ReferencingFactory.<Circuit>referenceFactory()).to(CIRCUIT_TYPE).in(RequestScoped.class);
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

	private final ApplicationHandler appHandler;

	protected AbstractWebComponent(ResourceConfig configuration) {
		ObjectMapper mapper = new ObjectMapper();

		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		mapper.disable(SerializationFeature.INDENT_OUTPUT);

		JacksonJsonProvider jacksonProvider = new JacksonJsonProvider(mapper);

		configuration.register(jacksonProvider);

		this.appHandler = createApplicationHandler(configuration);
	}

	protected abstract ApplicationHandler createApplicationHandler(ResourceConfig configuration);

	protected final ApplicationHandler getApplicationHandler() {
		return appHandler;
	}

	protected final ResourceConfig getResourceConfiguration() {
		return appHandler.getConfiguration();
	}

	protected final boolean service(RequestScopedInitializer initializer, Message message, boolean reportNoMatch) throws CircuitAbortException {
		try {
			ContainerRequest request = ScriptContainer.createContainerRequest(message);

			request.setRequestScopedInitializer(initializer);

			return service(request, message, reportNoMatch);
		} catch (IOException e) {
			throw new CircuitAbortException("Unable to create request", e);
		}
	}

	protected final boolean service(ContainerRequest request, Message message, boolean reportNoMatch) throws CircuitAbortException {
		boolean result = false;

		if (MessageContextTracker.getMessageContextTracker(message) == null) {
			throw new CircuitAbortException("no message context tracker available");
		}

		JerseyResponseWriter writer = new JerseyResponseWriter(message);

		if (!reportNoMatch) {
			/* disable no match */
			writer.reportMatch();
		}

		request.setWriter(writer);

		try {
			appHandler.handle(request);

			result = writer.matched();
		} catch (ContainerException e) {
			Throwable cause = e.getCause();

			if (cause instanceof CircuitAbortException) {
				throw (CircuitAbortException) cause;
			}

			throw e;
		}

		return result;
	}

	private static final String APPLICATION_TYPE = MediaType.APPLICATION_XHTML_XML_TYPE.getType();
	private static final String TEXT_TYPE = MediaType.TEXT_HTML_TYPE.getType();

	private static final String XHTML_SUBTYPE = MediaType.APPLICATION_XHTML_XML_TYPE.getSubtype();
	private static final String HTML_SUBTYPE = MediaType.TEXT_HTML_TYPE.getSubtype();

	private static Float getQuality(MediaType media) {
		String parameter = media.getParameters().get("q");
		Float quality = null;

		try {
			if ((parameter != null) && (parameter.trim().isEmpty())) {
				parameter = null;
			}

			if (parameter == null) {
				parameter = "1.0";
			}

			quality = Float.parseFloat(parameter);
		} catch (Exception e) {
			// ignore and report no quality
		}

		return quality;
	}

	/**
	 * filter xhtml and html content-types according to user agent accept header
	 * 
	 * @param variants list of available response variants
	 * @return filtered list of variants.
	 */
	private static List<Variant> filterVariants(HttpHeaders request, List<Variant> variants) {
		if (variants != null) {
			List<MediaType> accepted = request.getAcceptableMediaTypes();
			Float xhtml = null;
			Float html = null;

			if (accepted != null) {
				Iterator<MediaType> iterator = accepted.iterator();

				while (((xhtml == null) || (html == null)) && iterator.hasNext()) {
					MediaType cursor = iterator.next();

					if ((xhtml == null) && APPLICATION_TYPE.equals(cursor.getType()) && XHTML_SUBTYPE.equals(cursor.getSubtype())) {
						xhtml = getQuality(cursor);
					} else if ((html == null) && TEXT_TYPE.equals(cursor.getType()) && HTML_SUBTYPE.equals(cursor.getSubtype())) {
						html = getQuality(cursor);
					}
				}

				if ((xhtml != null) && (html != null) && (Float.compare(xhtml, html) == 0)) {
					/*
					 * do not advertise for html if xhtml is supported with the same 'q' parameter
					 */
					html = null;
				}

				List<Variant> filtered = new ArrayList<Variant>();

				for (Variant variant : variants) {
					MediaType cursor = variant.getMediaType();

					if (APPLICATION_TYPE.equals(cursor.getType()) && XHTML_SUBTYPE.equals(cursor.getSubtype())) {
						if ((xhtml != null)) {
							filtered.add(variant);
						}
					} else if (TEXT_TYPE.equals(cursor.getType()) && HTML_SUBTYPE.equals(cursor.getSubtype())) {
						if (html != null) {
							filtered.add(variant);
						}
					} else {
						filtered.add(variant);
					}
				}

				variants = Collections.unmodifiableList(filtered);
			}
		}

		return variants;
	}

	public static final Variant selectVariant(HttpHeaders headers, Request request, List<Variant> variants) {
		variants = filterVariants(headers, variants);

		return request.selectVariant(variants);
	}

	protected static class VordelApplication extends Application {
		private final Set<Class<?>> classes;
		private final Set<Object> singletons;
		private final Map<String, Object> properties;

		public VordelApplication(Set<Class<?>> classes, Set<Object> singletons, Map<String, Object> properties) {
			singletons = new HashSet<Object>(singletons);
			classes = new HashSet<Class<?>>(classes);

			singletons.add(VordelBodyProvider.getInstance());
			classes.add(JerseyExecutorFeature.class);
			classes.add(JerseyMethodMatchListener.class);
			classes.add(JerseyLoggingListener.class);

			this.classes = Collections.unmodifiableSet(classes);
			this.singletons = Collections.unmodifiableSet(singletons);
			this.properties = Collections.unmodifiableMap(new HashMap<String, Object>(properties));
		}

		@Override
		public Set<Class<?>> getClasses() {
			return classes;
		}

		@Override
		public Set<Object> getSingletons() {
			return singletons;
		}

		@Override
		public Map<String, Object> getProperties() {
			return properties;
		}
	}

	public static class Builder {
		protected final Map<String, Object> properties = new HashMap<String, Object>();
		protected final Set<Class<?>> classes = new HashSet<Class<?>>();
		protected final Set<Object> singletons = new HashSet<Object>();

		protected Builder() {
		}

		/**
		 * check if we are in policy studio of api gateway. usually, the class CertStore
		 * not loaded in policy studio.
		 * 
		 * @return 'true' if running on api gateway.
		 */
		public boolean buildable() {
			return CertStore.getInstance() != null;
		}

		public final Builder property(String key, Object value) {
			properties.put(key, value);

			return this;
		}

		public final Builder clazz(ClassLoader loader, String name) {
			if (loader == null) {
				Trace.error("called method without class loader");
			} else if (name == null) {
				Trace.error("called method without class name");
			} else {
				try {
					Class<?> clazz = loader.loadClass(name);

					if (MessageBodyWriter.class.isAssignableFrom(clazz)) {
						classes.add(clazz);
					} else {
						Trace.error(String.format("Class '%s' can't be used in JAX-RS Script", clazz.getName()));
					}
				} catch (ClassNotFoundException e) {
					Trace.error(String.format("Unable to load class '%s'", name), e);
				}
			}

			return this;
		}

		public final Builder writer(Class<? extends MessageBodyWriter<?>> writer) {
			if (writer != null) {
				classes.add(writer);
			}

			return this;
		}

		public final Builder writer(MessageBodyWriter<?> writer) {
			if (writer != null) {
				singletons.add(writer);
			}

			return this;
		}
	}
}
