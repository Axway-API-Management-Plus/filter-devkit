package com.vordel.circuit.filter.devkit.certmgr.export.jaxrs;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.security.cert.Certificate;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;

@Provider
public class PEMCertificateBodyWriter extends PEMBodyWriter<Certificate> {
	@Override
	public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return Certificate.class.isAssignableFrom(type);
	}

	@Override
	public long getSize(Certificate t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return -1;
	}
}
