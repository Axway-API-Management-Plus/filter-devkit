package com.vordel.circuit.filter.devkit.certmgr.export.jaxrs;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.security.cert.Certificate;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.Providers;

@Provider
public class PEMCertificateChainBodyWriter extends PEMBodyWriter<Certificate[]> {
	private final Providers providers;

	public PEMCertificateChainBodyWriter(@Context Providers providers) {
		this.providers = providers;
	}

	private MessageBodyWriter<Certificate> getCertificateWriter(Annotation[] annotations, MediaType mediaType) {
		return providers.getMessageBodyWriter(Certificate.class, Certificate.class, annotations, mediaType);
	}

	@Override
	public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return Certificate[].class.isAssignableFrom(type) && (getCertificateWriter(annotations, mediaType) != null);
	}

	@Override
	public void writeTo(Certificate[] t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
		MessageBodyWriter<Certificate> writer = getCertificateWriter(annotations, mediaType);

		for (Certificate certificate : t) {
			Class<? extends Certificate[]> clazz = t.getClass();

			writer.writeTo(certificate, clazz, clazz, annotations, mediaType, httpHeaders, entityStream);
		}
	}
}
