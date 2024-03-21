package com.vordel.circuit.filter.devkit.httpsig;

import java.io.IOException;
import java.io.OutputStream;

public interface EntityParser<E> {
	public boolean writeEntity(E entity, OutputStream out) throws IOException;
}
