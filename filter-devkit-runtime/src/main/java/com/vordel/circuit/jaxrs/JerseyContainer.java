package com.vordel.circuit.jaxrs;

import javax.ws.rs.core.GenericType;

import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spi.RequestScopedInitializer;

import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.script.context.MessageContextTracker;
import com.vordel.config.Circuit;
import com.vordel.trace.Trace;

public abstract class JerseyContainer {
	protected static final GenericType<Ref<Message>> MESSAGE_TYPE = new GenericType<Ref<Message>>() {};
	protected static final GenericType<Ref<MessageContextTracker>> MESSAGEPCONTEXTTRACKER_TYPE = new GenericType<Ref<MessageContextTracker>>() {};
	protected static final GenericType<Ref<MessageProcessor>> MESSAGEPROCESSOR_TYPE = new GenericType<Ref<MessageProcessor>>() {};
	protected static final GenericType<Ref<Circuit>> CIRCUIT_TYPE = new GenericType<Ref<Circuit>>() {};

	public static final JerseyContainer getInstance() {
		Class<?> clazz = null;

		try {
			try {
				/* check for jersey 2.31 and upwards */
				Class.forName("org.glassfish.jersey.internal.inject.InjectionManager");

				clazz = Class.forName("com.vordel.circuit.jaxrs.Jersey231Container");
			} catch (ClassNotFoundException e) {
				clazz = Class.forName("com.vordel.circuit.jaxrs.Jersey222Container");
			}
			
			return (JerseyContainer) clazz.newInstance();
		} catch (Exception fatal) {
			Trace.error("No Suitable Jersey runtime", fatal);
			
			throw new IllegalStateException("can't find jersey runtime", fatal);
		}
	}

	public abstract RequestScopedInitializer getRequestScopedInitializer(Message message);

	public abstract ApplicationHandler createApplicationHandler(ResourceConfig configuration);
}
