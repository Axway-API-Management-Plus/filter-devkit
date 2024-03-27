package com.vordel.circuit.filter.devkit.certmgr.export.jaxrs;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.security.cert.CertPath;
import java.security.cert.CertificateEncodingException;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

@Provider
@Produces(CertPathBodyWriter.PKIX_PKIPATH)
public class CertPathBodyWriter implements MessageBodyWriter<CertPath> {
	public static final String PKIX_PKIPATH = "application/pkix-pkipath";

	@Override
	public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return CertPath.class.isAssignableFrom(type);
	}

	@Override
	public long getSize(CertPath t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return -1;
	}

	@Override
	public void writeTo(CertPath t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
		try {
			byte[] encoded = t.getEncoded();

			entityStream.write(encoded);
		} catch (CertificateEncodingException e) {
			throw new WebApplicationException("Unable to encode Certificate", e, Status.INTERNAL_SERVER_ERROR);
		}
	}
}
