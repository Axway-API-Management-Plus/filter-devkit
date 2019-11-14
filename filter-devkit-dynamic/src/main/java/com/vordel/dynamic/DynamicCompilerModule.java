package com.vordel.dynamic;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Priority;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;

import com.vordel.circuit.script.bind.ExtensionModule;
import com.vordel.circuit.script.bind.ExtensionScanner;
import com.vordel.circuit.script.context.resources.SelectorResource;
import com.vordel.common.Dictionary;
import com.vordel.config.ConfigContext;
import com.vordel.dynamic.compiler.CompilerClassLoader;
import com.vordel.dynamic.compiler.CompilerFileManager;
import com.vordel.dynamic.compiler.DynamicJavaSource;
import com.vordel.el.Selector;
import com.vordel.es.EntityStoreException;
import com.vordel.trace.Trace;

/**
 * Deploy time compiler module. This module MUST NOT be used in production. its
 * purpose is to make fast tests on a local gateway. Compilation is
 * automatically activated if class files are detected in the right directory.
 * 
 * @author rdesaintleger@axway.com
 */
@Priority(Integer.MAX_VALUE)
public class DynamicCompilerModule implements ExtensionModule {
	private static final Selector<String> DIST_RESOURCES = SelectorResource.fromLiteral("${environment.VDISTDIR}/ext/dynamic", String.class, true);
	private static final Selector<String> INST_RESOURCES = SelectorResource.fromLiteral("${environment.VINSTDIR}/ext/dynamic", String.class, true);

	private DynamicCompilerModule() {
		/* inform the module if loaded */
		Trace.info("Dynamic compiler module loaded");
	}

	@Override
	public void attachModule(ConfigContext ctx) throws EntityStoreException {
		/* compile and register classes */
		ExtensionScanner.registerClasses(ctx, compile());
	}

	@Override
	public void detachModule() {
		/*
		 * since this module does not keep any information, there is nothing more to do
		 */
		Trace.info("Dynamic compiler module unloaded");
	}

	public static List<Class<?>> compile() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		File dist = new File(DIST_RESOURCES.substitute(Dictionary.empty));
		File inst = new File(INST_RESOURCES.substitute(Dictionary.empty));
		List<Class<?>> clazzes = new ArrayList<Class<?>>();

		if (compile(loader, dist, clazzes)) {
			Trace.info("Global dynamic classes compiled");
		} else {
			Trace.info("Global dynamic compiler disabled");
		}

		if (dist.equals(inst)) {
			Trace.info("Instance dynamic compiler running on Node Manager");
		} else if (compile(loader, inst, clazzes)) {
			Trace.info("Instance dynamic classes compiled");
		} else {
			Trace.info("Instance dynamic compiler disabled");
		}

		return clazzes;
	}

	public static boolean compile(ClassLoader loader, File root, List<Class<?>> clazzes) {
		boolean success = false;

		if ((clazzes != null) && (root != null) && root.exists() && root.isDirectory()) {
			Trace.info(String.format("compile classes from directory %s", root.getAbsolutePath()));

			try {
				/* compile classes */
				CompilerClassLoader compiled = compile(loader, root);

				clazzes = compiled.loadClasses(clazzes);
				success = true;
			} catch (IOException e) {
				Trace.error(String.format("can't compile classes from directory %s", root.getAbsolutePath()), e);
			}
		}

		return success;
	}

	public static CompilerClassLoader compile(ClassLoader parent, File root) throws IOException {
		JavaCompiler javac = new EclipseCompiler();
		CompilerListener diagnostic = new CompilerListener();
		StandardJavaFileManager sjfm = javac.getStandardFileManager(diagnostic, null, null);
		CompilerClassLoader loader = new CompilerClassLoader(parent, root);
		CompilerFileManager fileManager = new CompilerFileManager(sjfm, loader);
		List<String> options = new ArrayList<String>();

		options.add("-g");
		options.add("-target");
		options.add("1.8");

		sjfm.setLocation(StandardLocation.CLASS_PATH, ExtensionScanner.getClassPath(loader, new ArrayList<File>()));

		List<SimpleJavaFileObject> compilationUnits = scanFiles(new ArrayList<SimpleJavaFileObject>(), root, Collections.singleton("java"));

		Writer out = new StringWriter();
		JavaCompiler.CompilationTask compile = javac.getTask(out, fileManager, diagnostic, options, null, compilationUnits);

		boolean res = compile.call();

		if (!res) {
			Trace.error(out.toString());

			throw new EntityStoreException("Unable to compile !");
		}

		return loader;
	}

	private static List<SimpleJavaFileObject> scanFiles(List<SimpleJavaFileObject> output, File root, Collection<String> suffixes) throws IOException {
		if ((root != null) && root.exists() && (root.isDirectory())) {
			File[] files = root.listFiles();

			for (File file : files) {
				if (file.isDirectory()) {
					scanFiles(output, file, suffixes);
				} else {
					String path = file.getAbsolutePath();
					Iterator<String> iterator = suffixes.iterator();
					String suffix = null;

					while ((suffix == null) && iterator.hasNext()) {
						String cursor = iterator.next();

						if (path.endsWith(cursor)) {
							output.add(new DynamicJavaSource(file));
							suffix = cursor;
						}
					}
				}
			}
		}

		return output;
	}

	public static class CompilerListener implements DiagnosticListener<JavaFileObject> {
		@Override
		public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
			if (diagnostic != null) {
				Kind kind = diagnostic.getKind();
				JavaFileObject source = diagnostic.getSource();
				String message = diagnostic.getMessage(null);

				switch (kind) {
				case ERROR:
				case WARNING:
					Trace.error(String.format("%s: %s line %d", source.getName(), kind.name(), diagnostic.getLineNumber()));
					Trace.error(message);
					break;
				case MANDATORY_WARNING:
				case NOTE:
				case OTHER:
					Trace.info(message);
					break;
				default:
					break;
				}
			}
		}
	}
}
