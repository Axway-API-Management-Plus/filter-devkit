package com.vordel.circuit.filter.devkit.certmgr.export.jaxrs;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

@Provider
@Produces({ PEMBodyWriter.PEM_FILE, MediaType.TEXT_PLAIN })
public abstract class PEMBodyWriter<E> implements MessageBodyWriter<E> {
	public static final String PEM_FILE = "application/x-pem-file";

	@Override
	public long getSize(E t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return -1;
	}

	@Override
	public void writeTo(E t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
		OutputStreamWriter pem = new OutputStreamWriter(entityStream, StandardCharsets.UTF_8);
		JcaPEMWriter writer = new JcaPEMWriter(pem);

		try {
			writer.writeObject(t);
			writer.flush();
		} finally {
			writer.close();
			pem.close();
		}
	}
}
