package com.vordel.circuit.script.context;

import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.SecurityContext;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageCreationListener;
import com.vordel.circuit.MessageListenerAdapter;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.authn.AttributeAuthnProcessor;
import com.vordel.circuit.authn.HttpBasicProcessor;
import com.vordel.circuit.authn.HttpDigestProcessor;
import com.vordel.circuit.authn.SslProcessor;
import com.vordel.circuit.jaxrs.WebResourceContext;
import com.vordel.circuit.script.MessageAttributesLayers;
import com.vordel.circuit.script.MessageAttributesLayers.Layer;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.config.Circuit;
import com.vordel.dwe.http.HTTPMessage;
import com.vordel.dwe.http.ServerTransaction;
import com.vordel.el.Selector;
import com.vordel.es.ESPK;
import com.vordel.trace.Trace;

public final class MessageContextTracker {
	private static final Selector<String> AUTHENTICATION_SUBJECT = SelectorResource.fromExpression(MessageProperties.AUTHN_SUBJECT_ID, String.class);

	/**
	 * message processor call stack
	 */
	private final Deque<MessageProcessor> processors = new LinkedList<MessageProcessor>();
	/**
	 * circuit call stack
	 */
	private final Deque<Circuit> circuits = new LinkedList<Circuit>();

	private final Deque<WebResourceContext> contexts = new LinkedList<WebResourceContext>();
	
	private final Set<ESPK> initializers = new HashSet<ESPK>();

	private final MessageAttributesLayers union;

	private final Map<String, Object> globals;

	private final Message message;

	/**
	 * filter result history
	 */
	private FilterStatus status = null;

	private String authenticationScheme = null;

	private MessageContextTracker(Message message) {
		MessageAttributesLayers union = new MessageAttributesLayers();
		Map<String, Object> globals = null;

		/* retrieve top level attribute map */
		message.push(globals = message.pop());

		/* push global layer in attributes union */
		union.push(new Layer(globals, new HashSet<Object>(), new HashSet<Object>(), null, false));

		/* push attributes union into message */
		message.push(union);

		this.globals = globals;
		this.message = message;
		this.union = union;
	}

	/**
	 * Retrieve the MessageContextTracker associated to the message instance. If
	 * this method returns null, any component which require this object should
	 * throw a CurcuitAbortException. This object is not available during deployment
	 * time.
	 * 
	 * @param message current message
	 * @return MessageContextTracker object or null if none (module not loaded ?)
	 */
	public static MessageContextTracker getMessageContextTracker(Message message) {
		MessageContextTracker tracker = null;

		if ((load > 0) && (message != null)) {
			tracker = (MessageContextTracker) message.getMessageLocalStorage(slot);
		}

		return tracker;
	}

	public static WebResourceContext getWebResourceContext(Message message) {
		MessageContextTracker tracker = getMessageContextTracker(message);

		return tracker == null ? null : tracker.getWebResourceContext();
	}

	public void pushWebResourceContext(WebResourceContext context) {
		if ((context != null) && (!contexts.contains(context))) {
			contexts.addFirst(context);
		}
	}

	public void removeWebResourceContext(WebResourceContext context) {
		contexts.remove(context);
	}

	public WebResourceContext getWebResourceContext() {
		return contexts.peekFirst();
	}

	public String getAuthenticationScheme() {
		return getAuthenticationSubject() == null ? null : authenticationScheme;
	}

	/**
	 * retrieve authentication username from ${authentication.subject.id} selector.
	 * 
	 * @return authenticated username.
	 */
	public String getAuthenticationSubject() {
		return AUTHENTICATION_SUBJECT.substitute(message);
	}

	/**
	 * Retrieve message root context.
	 * 
	 * @return message global map.
	 */
	public Map<String, Object> getMessageGlobals() {
		return globals;
	}

	public MessageAttributesLayers getLayeredAttributes() {
		return union;
	}

	/**
	 * check if message comes from a secure https channel. This implementation does
	 * not rely against message attribute. It uses the embedded server transaction
	 * instead.
	 * 
	 * @return 'true' if messages is on a secure channel, 'false' otherwise.
	 */
	public boolean isSecure() {
		return isSecure(message);
	}
	
	public static boolean isSecure(Message message) {
		ServerTransaction txn = message instanceof HTTPMessage ? ((HTTPMessage) message).txn : null;

		return txn == null ? false : txn.getCipherName() != null;
	}

	/**
	 * @return the current message processor
	 */
	public MessageProcessor getMessageProcessor() {
		return processors.peekFirst();
	}

	/**
	 * @return the current circuit
	 */
	public Circuit getCircuit() {
		return circuits.peekFirst();
	}

	/**
	 * return status of previous filter execution
	 * 
	 * @param index negative index (-1 is the last filter)
	 * @return 'true' or 'false' depending on previous filter result.
	 * @throws CircuitAbortException if the filter did exit with an exception
	 */
	public boolean getPreviousStatus(int index) throws CircuitAbortException {
		FilterStatus result = null;
		FilterStatus last = status;

		while ((index < 0) && (last != null)) {
			index += 1;

			result = last;
			last = last.previous;
		}

		if (index < 0) {
			result = null;
		}

		return result == null ? false : result.getStatus();
	}

	public Set<ESPK> getInitializers() {
		return initializers;
	}

	private void status(boolean result, CircuitAbortException error) {
		status = new FilterStatus(status, result, error);
	}

	private void push(Circuit circuit, MessageProcessor processor) {
		processors.addFirst(processor);
		circuits.addFirst(circuit);
	}

	private void pop() {
		processors.pollFirst();
		circuits.pollFirst();
	}

	private static MessageContextCreator creator = null;
	private static int load = 0;
	private static int slot = -1;

	public static final class MessageContextCreator implements MessageCreationListener {
		/**
		 * load the message context creator as message creation listener. This method
		 * MUST NOT be called when handling messages. It can be called multiple times
		 * from attach methods.
		 */
		public static void load() {
			if (load == 0) {
				/*
				 * creator has not been registered yet, allocate the message local store slot
				 * and create the creation listener instance.
				 */
				slot = Message.registerMessageLocalStorage();

				Message.addCreationListener(creator = new MessageContextCreator());

				Trace.info("MessageContextTracker slot allocated");
			}

			if (load < Integer.MAX_VALUE) {
				/* just in case, handle integer overflow */
				load += 1;
			}
		}

		/**
		 * unload the message context creator. This method MUST NOT be called when
		 * handling messages. It can be called multiple times from detach methods.
		 */
		public static void unload() {
			if (load > 0) {
				/* just in case, handle integer overflow */
				load -= 1;
			}

			if (load == 0) {
				/*
				 * reference count of creator has reached 0, unregister
				 */
				Message.deregisterMessageLocalStorage(slot);
				Message.removeCreationListener(creator);

				creator = null;
				slot = -1;

				Trace.info("MessageContextTracker slot released");
			}
		}

		private MessageContextCreator() {
		}

		@Override
		public void messageCreated(Message message, Object source) {
			MessageContextTracker tracker = new MessageContextTracker(message);
			MessageContextListener listener = new MessageContextListener(tracker);

			message.addMessageListener(listener);
			message.setMessageLocalStorage(slot, tracker);
		}
	}

	public static final class MessageContextListener extends MessageListenerAdapter {
		private final MessageContextTracker tracker;

		private MessageContextListener(MessageContextTracker tracker) {
			this.tracker = tracker;
		}

		@Override
		public void preFilterInvocation(Circuit circuit, MessageProcessor processor, Message message, MessageProcessor caller, Object ctx) {
			super.preFilterInvocation(circuit, processor, message, caller, ctx);

			tracker.push(circuit, processor);
		}

		@Override
		public void postFilterInvocation(Circuit circuit, MessageProcessor processor, Message message, int resultType, MessageProcessor caller, Object ctx) {
			super.postFilterInvocation(circuit, processor, message, resultType, caller, ctx);

			switch (resultType) {
			case 1:
				/* filter is in success check if it was an authentication */
				if (processor instanceof HttpBasicProcessor) {
					tracker.authenticationScheme = SecurityContext.BASIC_AUTH;
				} else if (processor instanceof HttpDigestProcessor) {
					tracker.authenticationScheme = SecurityContext.DIGEST_AUTH;
				} else if (processor instanceof AttributeAuthnProcessor) {
					tracker.authenticationScheme = SecurityContext.FORM_AUTH;
				} else if (processor instanceof SslProcessor) {
					tracker.authenticationScheme = SecurityContext.CLIENT_CERT_AUTH;
				} else {
					tracker.authenticationScheme = null;
				}
			case 0:
				tracker.status(resultType == 1 ? true : false, null);
				/* exceptions will be handled in the preFaultHandlerInvocation() method */
			default:
				tracker.pop();
				break;
			}
		}

		@Override
		public void preFaultHandlerInvocation(Circuit circuit, MessageProcessor faultHandler, Message message, CircuitAbortException reason) {
			super.preFaultHandlerInvocation(circuit, faultHandler, message, reason);

			tracker.status(false, reason);
		}
	}

	private static class FilterStatus {
		private final FilterStatus previous;
		private final CircuitAbortException error;

		private final boolean result;

		public FilterStatus(FilterStatus previous, boolean result, CircuitAbortException error) {
			this.previous = previous;
			this.result = result;
			this.error = error;
		}

		public boolean getStatus() throws CircuitAbortException {
			if (error != null) {
				throw error;
			}

			return result;
		}
	}
}
