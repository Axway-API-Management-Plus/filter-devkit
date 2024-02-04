package com.vordel.circuit.filter.devkit.context;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/*
 * XXX using repackaged is BAD, really bad... but it allows to support Java 8 instead of 7 using asm 4.0
 * maybe we should repackage ourselves the last asm release.
 */
import jersey.repackaged.org.objectweb.asm.AnnotationVisitor;
import jersey.repackaged.org.objectweb.asm.ClassReader;
import jersey.repackaged.org.objectweb.asm.ClassVisitor;
import jersey.repackaged.org.objectweb.asm.Opcodes;

class ExtensionAcceptingListener {
	/**
	 * keep track of discovered hierarchy
	 */
	private Map<String, String> superMap = new HashMap<String, String>();

	private final Set<String> annotations;
	private final Set<String> interfaces;
	private final Set<String> superClazzes;
	private final Set<String> clazzes;

	private final AnnotatedClassVisitor visitor;


	public ExtensionAcceptingListener(Set<String> clazzes, Class<?>... classes) {
		Set<String> annotations = new HashSet<String>();
		Set<String> interfaces = new HashSet<String>();
		Set<String> superClazzes = new HashSet<String>();

		fillNames(annotations, interfaces, superClazzes, classes);

		this.annotations = annotations;
		this.interfaces = interfaces;
		this.superClazzes = superClazzes;
		this.clazzes = clazzes;

		this.visitor = new AnnotatedClassVisitor();
	}

	private static void fillNames(Set<String> annotations, Set<String> interfaces, Set<String> superClazzes, Class<?>... classes) {
		for (Class<?> c : classes) {
			String name = c.getName().replaceAll("\\.", "/");

			if (c.isAnnotation()) {
				annotations.add("L" + name + ";");
			} else if (c.isInterface()) {
				interfaces.add(name);
			} else {
				superClazzes.add(name);
			}
		}
	}

	public void process(final InputStream in) throws IOException {
		ClassReader reader = new ClassReader(in);

		reader.accept(visitor, 0);
	}

	public void processInheritance() {
		for (String name : superMap.keySet()) {
			processInheritance(name);
		}
	}

	private boolean processInheritance(String name) {
		boolean inherited = clazzes.contains(name);

		if (!inherited) {
			String superName = superMap.get(name);

			if ((superName != null) && (inherited = processInheritance(superName))) {
				clazzes.add(name);
			}
		}

		return inherited;
	}

	private final class AnnotatedClassVisitor extends ClassVisitor {
		/**
		 * The name of the visited class.
		 */
		private String className;
		/**
		 * True if the class has the correct scope
		 */
		private boolean isScoped;
		/**
		 * True if the class has the correct declared annotations
		 */
		private boolean isAnnotated;
		/**
		 * True if the class has the correct declared interface
		 */
		private boolean hasInterface;
		/**
		 * True if the class extends the correct class
		 */
		private boolean hasSuperClass;

		public AnnotatedClassVisitor() {
			super(Opcodes.ASM5);
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] implemented) {
			className = name;
			isScoped = (access & Opcodes.ACC_PUBLIC) != 0;
			isAnnotated = false;
			hasInterface = false;
			hasSuperClass = false;

			for (String desc : implemented) {
				hasInterface |= interfaces.contains(desc);
			}

			if ((superName != null) && (!name.equals(superName))) {
				hasSuperClass |= superClazzes.contains(superName);

				superMap.put(name.replaceAll("/", "."), superName.replaceAll("/", "."));
			}

			super.visit(version, access, name, signature, superName, implemented);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			isAnnotated |= annotations.contains(desc);

			return super.visitAnnotation(desc, visible);
		}

		@Override
		public void visitInnerClass(String name, String outerName, String innerName, int access) {
			if (className.equals(name)) {
				isScoped = (access & Opcodes.ACC_PUBLIC) != 0;

				// Inner classes need to be statically scoped
				isScoped &= (access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC;
			}

			super.visitInnerClass(name, outerName, innerName, access);
		}

		@Override
		public void visitEnd() {
			if (isScoped && (isAnnotated || hasInterface || hasSuperClass)) {
				String name = className.replaceAll("/", ".");

				clazzes.add(name);
			}

			super.visitEnd();
		}
	}
}
