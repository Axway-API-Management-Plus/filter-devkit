package com.vordel.circuit.jaxrs;

import javax.ws.rs.ext.Provider;

import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.glassfish.jersey.server.spi.ContainerResponseWriter;

import com.vordel.circuit.jaxrs.JerseyResponseWriter;

@Provider
public class JerseyMethodMatchListener implements RequestEventListener, ApplicationEventListener {
	@Override
	public void onEvent(RequestEvent event) {
		switch (event.getType()) {
		case RESOURCE_METHOD_START:
			ContainerRequest request = event.getContainerRequest();
			ContainerResponseWriter writer = request.getResponseWriter();

			if (writer instanceof JerseyResponseWriter) {
				/* report that we have at least a match for a method */
				((JerseyResponseWriter) writer).reportMatch();
			}
			break;
		default:
			break;
		}
	}

	@Override
	public void onEvent(ApplicationEvent event) {
	}

	@Override
	public RequestEventListener onRequest(RequestEvent requestEvent) {
		return this;
	}

}
