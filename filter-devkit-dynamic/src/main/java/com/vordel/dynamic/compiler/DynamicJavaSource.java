package com.vordel.dynamic.compiler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;

import javax.tools.SimpleJavaFileObject;

public class DynamicJavaSource extends SimpleJavaFileObject {
	private String src;

	public DynamicJavaSource(URI path, String content) {
		super(path, Kind.SOURCE);

		this.src = content;
	}

	public DynamicJavaSource(File file) throws IOException {
		this(file.toURI(), read(file));
	}

	private static final String read(File file) throws IOException {
		InputStream input = new FileInputStream(file);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Charset utf8 = Charset.forName("UTF-8");

		try {
			int read = -1;

			while ((read = input.read()) != -1) {
				baos.write(read);
			}
		} finally {
			input.close();
		}

		return new String(baos.toByteArray(), utf8);
	}

	public CharSequence getCharContent(boolean ignoreEncodingErrors) {
		return src;
	}

	public OutputStream openOutputStream() {
		throw new IllegalStateException();
	}

	public InputStream openInputStream() {
		Charset utf8 = Charset.forName("UTF-8");

		return new ByteArrayInputStream(src.getBytes(utf8));
	}
}