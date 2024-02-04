package com.vordel.circuit.filter.devkit.dynamic.compiler;

import java.io.IOException;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

public class CompilerFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
	private final CompilerClassLoader xcl;

	public CompilerFileManager(StandardJavaFileManager sjfm, CompilerClassLoader xcl) {
		super(sjfm);

		this.xcl = xcl;
	}

	public JavaFileObject getJavaFileForOutput(Location location, String name, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
		DynamicByteCode mbc = new DynamicByteCode(name);

		xcl.defineClass(name, mbc);

		return mbc;
	}

	public ClassLoader getClassLoader(Location location) {
		return xcl;
	}
}