package com.vordel.dynamic.compiler;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import javax.tools.SimpleJavaFileObject;

public class DynamicByteCode extends SimpleJavaFileObject {
	private ByteArrayOutputStream baos;

	public DynamicByteCode(String name) {
		super(URI.create(name), Kind.CLASS);
	}

	public CharSequence getCharContent(boolean ignoreEncodingErrors) {
		throw new IllegalStateException();
	}

	public OutputStream openOutputStream() {
		baos = new ByteArrayOutputStream();

		return baos;
	}

	public InputStream openInputStream() {
		throw new IllegalStateException();
	}

	public byte[] getBytes() {
		return baos.toByteArray();
	}
}