package com.vordel.circuit.filter.devkit.certmgr.export.jaxrs;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.security.PublicKey;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;

@Provider
public class PEMPublicKeyBodyWriter extends PEMBodyWriter<PublicKey> {
	@Override
	public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return PublicKey.class.isAssignableFrom(type);
	}
}
