package com.vordel.circuit.filter.devkit.jaxrs;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URI;
import java.security.Principal;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.TimeoutHandler;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Variant;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Providers;

import com.vordel.circuit.Message;

public final class WebResourceContext implements UriInfo, HttpHeaders, Request, SecurityContext, Providers, AsyncResponse {
	private final int id;

	private final UriInfo uriInfo;
	private final HttpHeaders httpHeaders;
	private final Request request;
	private final SecurityContext securityContext;
	private final Providers providers;
	private final AsyncResponse asyncResponse;

	private static final Object SYNC = new Object();
	private static int count = 0;

	@Inject
	private WebResourceContext(Message message, UriInfo uriInfo, HttpHeaders httpHeaders, Request request, SecurityContext securityContext, Providers providers, AsyncResponse asyncResponse) {
		this.uriInfo = uriInfo;
		this.httpHeaders = httpHeaders;
		this.request = request;
		this.securityContext = securityContext;
		this.providers = providers;
		this.asyncResponse = asyncResponse;

		synchronized (SYNC) {
			this.id = count++;
		}
	}

	@Override
	public int hashCode() {
		return Integer.valueOf(id).hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		boolean equals = obj == this;

		if ((!equals) && (obj instanceof WebResourceContext)) {
			equals = ((WebResourceContext) obj).id == this.id;
		}

		return equals;
	}

	@Override
	public boolean resume(Object response) {
		return asyncResponse.resume(response);
	}

	@Override
	public boolean resume(Throwable response) {
		return asyncResponse.resume(response);
	}

	@Override
	public boolean cancel() {
		return asyncResponse.cancel();
	}

	@Override
	public boolean cancel(int retryAfter) {
		return asyncResponse.cancel(retryAfter);
	}

	@Override
	public boolean cancel(Date retryAfter) {
		return asyncResponse.cancel(retryAfter);
	}

	@Override
	public boolean isSuspended() {
		return asyncResponse.isSuspended();
	}

	@Override
	public boolean isCancelled() {
		return asyncResponse.isCancelled();
	}

	@Override
	public boolean isDone() {
		return asyncResponse.isDone();
	}

	@Override
	public boolean setTimeout(long time, TimeUnit unit) {
		return asyncResponse.setTimeout(time, unit);
	}

	@Override
	public void setTimeoutHandler(TimeoutHandler handler) {
		asyncResponse.setTimeoutHandler(handler);
	}

	@Override
	public Collection<Class<?>> register(Class<?> callback) {
		return asyncResponse.register(callback);
	}

	@Override
	public Map<Class<?>, Collection<Class<?>>> register(Class<?> callback, Class<?>... callbacks) {
		return asyncResponse.register(callback, callbacks);
	}

	@Override
	public Collection<Class<?>> register(Object callback) {
		return asyncResponse.register(callback);
	}

	@Override
	public Map<Class<?>, Collection<Class<?>>> register(Object callback, Object... callbacks) {
		return asyncResponse.register(callback, callbacks);
	}

	@Override
	public <T> MessageBodyReader<T> getMessageBodyReader(Class<T> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return providers.getMessageBodyReader(type, genericType, annotations, mediaType);
	}

	@Override
	public <T> MessageBodyWriter<T> getMessageBodyWriter(Class<T> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return providers.getMessageBodyWriter(type, genericType, annotations, mediaType);
	}

	@Override
	public <T extends Throwable> ExceptionMapper<T> getExceptionMapper(Class<T> type) {
		return providers.getExceptionMapper(type);
	}

	@Override
	public <T> ContextResolver<T> getContextResolver(Class<T> contextType, MediaType mediaType) {
		return providers.getContextResolver(contextType, mediaType);
	}

	@Override
	public Principal getUserPrincipal() {
		return securityContext.getUserPrincipal();
	}

	@Override
	public boolean isUserInRole(String role) {
		return securityContext.isUserInRole(role);
	}

	@Override
	public boolean isSecure() {
		return securityContext.isSecure();
	}

	@Override
	public String getAuthenticationScheme() {
		return securityContext.getAuthenticationScheme();
	}

	@Override
	public String getMethod() {
		return request.getMethod();
	}

	@Override
	public Variant selectVariant(List<Variant> variants) {
		return request.selectVariant(variants);
	}

	@Override
	public ResponseBuilder evaluatePreconditions(EntityTag eTag) {
		return request.evaluatePreconditions(eTag);
	}

	@Override
	public ResponseBuilder evaluatePreconditions(Date lastModified) {
		return request.evaluatePreconditions(lastModified);
	}

	@Override
	public ResponseBuilder evaluatePreconditions(Date lastModified, EntityTag eTag) {
		return request.evaluatePreconditions(lastModified, eTag);
	}

	@Override
	public ResponseBuilder evaluatePreconditions() {
		return request.evaluatePreconditions();
	}

	@Override
	public List<String> getRequestHeader(String name) {
		return httpHeaders.getRequestHeader(name);
	}

	@Override
	public String getHeaderString(String name) {
		return httpHeaders.getHeaderString(name);
	}

	@Override
	public MultivaluedMap<String, String> getRequestHeaders() {
		return httpHeaders.getRequestHeaders();
	}

	@Override
	public List<MediaType> getAcceptableMediaTypes() {
		return httpHeaders.getAcceptableMediaTypes();
	}

	@Override
	public List<Locale> getAcceptableLanguages() {
		return httpHeaders.getAcceptableLanguages();
	}

	@Override
	public MediaType getMediaType() {
		return httpHeaders.getMediaType();
	}

	@Override
	public Locale getLanguage() {
		return httpHeaders.getLanguage();
	}

	@Override
	public Map<String, Cookie> getCookies() {
		return httpHeaders.getCookies();
	}

	@Override
	public Date getDate() {
		return httpHeaders.getDate();
	}

	@Override
	public int getLength() {
		return httpHeaders.getLength();
	}

	@Override
	public String getPath() {
		return uriInfo.getPath();
	}

	@Override
	public String getPath(boolean decode) {
		return uriInfo.getPath(decode);
	}

	@Override
	public List<PathSegment> getPathSegments() {
		return uriInfo.getPathSegments();
	}

	@Override
	public List<PathSegment> getPathSegments(boolean decode) {
		return uriInfo.getPathSegments(decode);
	}

	@Override
	public URI getRequestUri() {
		return uriInfo.getRequestUri();
	}

	@Override
	public UriBuilder getRequestUriBuilder() {
		return uriInfo.getRequestUriBuilder();
	}

	@Override
	public URI getAbsolutePath() {
		return uriInfo.getAbsolutePath();
	}

	@Override
	public UriBuilder getAbsolutePathBuilder() {
		return uriInfo.getAbsolutePathBuilder();
	}

	@Override
	public URI getBaseUri() {
		return uriInfo.getBaseUri();
	}

	@Override
	public UriBuilder getBaseUriBuilder() {
		return uriInfo.getBaseUriBuilder();
	}

	@Override
	public MultivaluedMap<String, String> getPathParameters() {
		return uriInfo.getPathParameters();
	}

	@Override
	public MultivaluedMap<String, String> getPathParameters(boolean decode) {
		return uriInfo.getPathParameters(decode);
	}

	@Override
	public MultivaluedMap<String, String> getQueryParameters() {
		return uriInfo.getQueryParameters();
	}

	@Override
	public MultivaluedMap<String, String> getQueryParameters(boolean decode) {
		return uriInfo.getQueryParameters(decode);
	}

	@Override
	public List<String> getMatchedURIs() {
		return uriInfo.getMatchedURIs();
	}

	@Override
	public List<String> getMatchedURIs(boolean decode) {
		return uriInfo.getMatchedURIs(decode);
	}

	@Override
	public List<Object> getMatchedResources() {
		return uriInfo.getMatchedResources();
	}

	@Override
	public URI resolve(URI uri) {
		return uriInfo.resolve(uri);
	}

	@Override
	public URI relativize(URI uri) {
		return uriInfo.relativize(uri);
	}
}
