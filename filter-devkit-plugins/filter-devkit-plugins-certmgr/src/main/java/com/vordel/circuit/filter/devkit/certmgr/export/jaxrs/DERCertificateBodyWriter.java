package com.vordel.circuit.filter.devkit.certmgr.export.jaxrs;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

@Provider
@Produces(DERCertificateBodyWriter.PKIX_CERT)
public class DERCertificateBodyWriter implements MessageBodyWriter<Certificate> {
	public static final String PKIX_CERT = "application/pkix-cert";

	@Override
	public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return Certificate.class.isAssignableFrom(type);
	}

	@Override
	public long getSize(Certificate t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return -1;
	}

	@Override
	public void writeTo(Certificate t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
		try {
			byte[] encoded = t.getEncoded();

			entityStream.write(encoded);
		} catch (CertificateEncodingException e) {
			throw new WebApplicationException("Unable to encode Certificate", e, Response.serverError().build());
		}
	}
}
