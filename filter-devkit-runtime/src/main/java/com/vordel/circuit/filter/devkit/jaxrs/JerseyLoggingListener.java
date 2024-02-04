package com.vordel.circuit.filter.devkit.jaxrs;

import java.io.IOException;

import javax.annotation.Priority;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

import com.vordel.trace.Trace;

@PreMatching
@Priority(Integer.MIN_VALUE)
public class JerseyLoggingListener implements RequestEventListener, ApplicationEventListener, ContainerRequestFilter, ContainerResponseFilter, WriterInterceptor {
	@Override
	public void onEvent(ApplicationEvent event) {
		Trace.debug(String.format("jax-rs application event : %s", event.getType()));
	}

	@Override
	public RequestEventListener onRequest(RequestEvent requestEvent) {
		Trace.debug(String.format("jax-rs request event : %s (in application event listener)", requestEvent.getType()));

		return this;
	}

	@Override
	public void onEvent(RequestEvent event) {
		if (Trace.isDebugEnabled()) {
			Trace.debug(String.format("jax-rs request event : %s", event.getType()));
			ExtendedUriInfo uriInfo = null;

			switch (event.getType()) {
			case RESOURCE_METHOD_START:
				uriInfo = event.getUriInfo();

				Trace.debug(String.format("invoke '%s'", uriInfo.getMatchedResourceMethod().getInvocable().getDefinitionMethod()));
				break;
			case EXCEPTION_MAPPER_FOUND:
			case EXCEPTION_MAPPING_FINISHED:
			case FINISHED:
			case LOCATOR_MATCHED:
			case MATCHING_START:
			case ON_EXCEPTION:
			case REQUEST_FILTERED:
			case REQUEST_MATCHED:
			case RESOURCE_METHOD_FINISHED:
			case RESP_FILTERS_FINISHED:
			case RESP_FILTERS_START:
			case START:
			case SUBRESOURCE_LOCATED:
			default:
				break;
			}
		}
	}

	@Override
	public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
		if (context.getEntity() != null) {
			MediaType mediaType = context.getMediaType();

			if (mediaType == null) {
				Trace.debug("jax-rs returned an entity without media type");
			} else {
				Trace.debug(String.format("jax-rs returned an entity of type '%s'", context.getMediaType()));
			}
		} else {
			Trace.debug("jax-rs returned no entity");
		}

		context.proceed();
	}

	@Override
	public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
		if (responseContext.getEntity() == null) {
			Trace.debug(String.format("jax-rs response : %d (no entity)", responseContext.getStatus()));
		} else {
			Trace.debug(String.format("jax-rs response : %d %s", responseContext.getStatus(), responseContext.getMediaType()));
		}
	}

	@Override
	public void filter(ContainerRequestContext requestContext) throws IOException {
		Trace.debug(String.format("jax-rs request : %s %s", requestContext.getMethod(), requestContext.getUriInfo().getRequestUri()));
	}

}
