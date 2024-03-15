package com.vordel.circuit.filter.devkit.dynamic;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Priority;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;

import com.vordel.circuit.filter.devkit.context.ExtensionClassLoader;
import com.vordel.circuit.filter.devkit.context.ExtensionLoader;
import com.vordel.circuit.filter.devkit.context.ExtensionModule;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionInstance;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionLibraries;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionLink;
import com.vordel.circuit.filter.devkit.context.resources.SelectorResource;
import com.vordel.circuit.filter.devkit.dynamic.compiler.CompilerFileManager;
import com.vordel.circuit.filter.devkit.dynamic.compiler.DynamicJavaSource;
import com.vordel.common.Dictionary;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.es.EntityStoreException;
import com.vordel.trace.Trace;

/**
 * Deploy time compiler module. Main purpose of this module is to make tests on
 * a local API Gateway. It compiles classes on the fly to a dedicated directory.
 * Annotation processing is supported and a 'child first' class loader is used
 * to hide annotation processors and compiler libraries from the API Gateway
 * class path.
 * 
 * Once compilation is done classes are scanned using the regular extension
 * mechanism.
 * 
 * @author rdesaintleger@axway.com
 */
@Priority(Integer.MAX_VALUE)
@ExtensionInstance
@ExtensionLibraries("${environment.VDISTDIR}/ext/extra/compiler")
public class DynamicCompilerModule implements ExtensionModule {
	private static final Selector<String> DIST_RESOURCES = SelectorResource.fromLiteral("${environment.VDISTDIR}/ext/dynamic", String.class, true);
	private static final Selector<String> INST_RESOURCES = SelectorResource.fromLiteral("${environment.VINSTDIR}/ext/dynamic", String.class, true);

	private static final Selector<String> DIST_CLASSES = SelectorResource.fromLiteral("${environment.VDISTDIR}/ext/extra/compiled", String.class, true);
	private static final Selector<String> INST_CLASSES = SelectorResource.fromLiteral("${environment.VINSTDIR}/ext/extra/compiled", String.class, true);

	private DynamicCompilerModule() {
		/* inform the module is loaded */
		Trace.info("Dynamic compiler module loaded");
	}

	@Override
	public void attachModule(ConfigContext ctx) throws EntityStoreException {
		/* assume extension class loader is the base class loader */
		ClassLoader loader = ExtensionLoader.class.getClassLoader();

		/* retrieve source directories */
		File distsrc = new File(DIST_RESOURCES.substitute(Dictionary.empty));
		File instsrc = new File(INST_RESOURCES.substitute(Dictionary.empty));

		/* cleanup output directories */
		File distclasses = new File(DIST_CLASSES.substitute(Dictionary.empty));
		File instclasses = new File(INST_CLASSES.substitute(Dictionary.empty));

		deleteClasses(distclasses);
		deleteClasses(instclasses);

		/* try to compile installation wide dynamic classes */
		ClassLoader compiled = compile(loader, distsrc, distclasses);

		if (compiled != null) {
			Trace.info("Global dynamic classes compiled");

			/* scan newly discovered classes */
			ExtensionLoader.scanClasses(ctx, loader = compiled);
		} else {
			/* no classloader returned directory does not exists */
			Trace.info("Global dynamic compiler disabled");
		}

		if (distsrc.equals(instsrc)) {
			Trace.info("Instance dynamic compiler running on Node Manager");
		} else {
			/* try to compile instance specific dynamic classes */
			compiled = compile(loader, instsrc, instclasses);

			if (compiled != null) {
				Trace.info("Instance dynamic classes compiled");

				/* scan newly discovered classes */
				ExtensionLoader.scanClasses(ctx, compiled);
			} else {
				/* no classloader returned directory does not exists */
				Trace.info("Instance dynamic compiler disabled");
			}
		}
	}

	@Override
	public void detachModule() {
		/* removing compiled classes when detaching */
		File distclasses = new File(DIST_CLASSES.substitute(Dictionary.empty));
		File instclasses = new File(INST_CLASSES.substitute(Dictionary.empty));

		deleteClasses(distclasses);
		deleteClasses(instclasses);

		/*
		 * since this module does not keep any information, there is nothing more to do
		 */
		Trace.info("Dynamic compiler module unloaded");
	}

	/**
	 * recursively delete a file or directory
	 * 
	 * @param classes file to be deleted
	 */
	private static void deleteClasses(File classes) {
		if (classes.isDirectory()) {
			for (File file : classes.listFiles()) {
				deleteClasses(file);
			}
		}

		classes.delete();
	}

	/**
	 * compile entry point. check if source directory exists and create output
	 * directory.
	 * 
	 * @param loader parent class loader
	 * @param src    source directory
	 * @param output output class directory
	 * @return a classloader with compiled classes or null if source directory does
	 *         not exists.
	 */
	private static ClassLoader compile(ClassLoader loader, File src, File output) {
		if ((src != null) && src.exists() && src.isDirectory()) {
			if (!output.mkdirs()) {
				Trace.error(String.format("unable to create directory %s", output.getAbsolutePath()));
			} else {
				try {
					Trace.info(String.format("compile classes from directory %s", src.getAbsolutePath()));

					/* compile classes */
					return runCompiler(loader, src, output);
				} catch (IOException e) {
					Trace.error(String.format("can't compile classes from directory %s", src.getAbsolutePath()), e);
				}
			}
		}

		return null;
	}

	/**
	 * create and run the compiler task. compilation is done using the full class
	 * path (tools, compiler and api gateway). returned class loader can't access
	 * tools and compiler.
	 * 
	 * @param parent parent class loader
	 * @param src    source directory
	 * @param output output class directory
	 * @return a classloader with compiled classes or null if source directory does
	 *         not exists.
	 * @throws IOException if unable to read source files
	 */
	private static ClassLoader runCompiler(ClassLoader parent, File src, File output) throws IOException {
		JavaCompiler javac = new EclipseCompiler();
		CompilerListener diagnostic = new CompilerListener();
		StandardJavaFileManager sjfm = javac.getStandardFileManager(diagnostic, null, null);
		ClassLoader loader = getCompiledClassLoader(parent, src, output);
		CompilerFileManager fileManager = new CompilerFileManager(sjfm, loader, DynamicCompilerModule.class.getClassLoader());
		List<String> options = new ArrayList<String>();
		List<File> jars = new ArrayList<File>();

		/* keep line numbers and variables names for debug */
		options.add("-g");

		/* set target to java 11 */
		options.add("-target");
		options.add("11");

		/* gather all jars on class path */
		getClassPath(DynamicCompilerModule.class.getClassLoader(), parent, jars, src, output);

		/* sets compiler class path with tools class loader */
		sjfm.setLocation(StandardLocation.CLASS_PATH, jars);
		sjfm.setLocation(StandardLocation.SOURCE_PATH, Collections.singleton(src));
		sjfm.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(output));
		sjfm.setLocation(StandardLocation.SOURCE_OUTPUT, Collections.singleton(output));

		/* scan files to be compiled */
		List<SimpleJavaFileObject> compilationUnits = scanFiles(new ArrayList<SimpleJavaFileObject>(), src, Collections.singleton("java"));

		Writer out = new StringWriter();
		JavaCompiler.CompilationTask compile = javac.getTask(out, fileManager, diagnostic, options, null, compilationUnits);
		boolean res = compile.call();

		if (!res) {
			Trace.error(out.toString());

			return null;
		}

		return loader;
	}

	/**
	 * create a class loader suitable for dynamic compiled classes
	 * 
	 * @param parent parent class loader.
	 * @param files  set of directories to be added to the returned class loader
	 *               (usualy source for resource and output for class files).
	 * @return class loader for dynamic compiled classes
	 */
	private static ClassLoader getCompiledClassLoader(ClassLoader parent, File... files) {
		List<URL> urls = new ArrayList<URL>();

		for (int index = 0; index < files.length; index++) {
			File file = files[index];

			if ((file != null) && file.isDirectory()) {
				try {
					urls.add(file.toURI().toURL());
				} catch (Exception e) {
					Trace.error(String.format("unable to add file URL '%s' to classpath", files[index]), e);
				}
			}
		}

		return new ExtensionClassLoader(urls.toArray(new URL[0]), parent);
	}

	/**
	 * retrieve class path set of URLs
	 * 
	 * @param tools  tools class loader
	 * @param loader api gateway class loader
	 * @param jars   list of discovered jars
	 * @param files  directories to be added to class path (usually source for
	 *               resources and output for classes)
	 */
	private static void getClassPath(ClassLoader tools, ClassLoader loader, List<File> jars, File... files) {
		Set<File> seen = new HashSet<File>();

		getClassPath(tools, jars, seen);
		getClassPath(loader, jars, seen);

		for (int index = 0; index < files.length; index++) {
			File file = files[index];

			if ((file != null) && file.isDirectory() && seen.add(file)) {
				jars.add(file);
			}
		}
	}

	private static void getClassPath(ClassLoader loader, List<File> jars, Set<File> seen) {
		if (loader != null) {
			getClassPath(loader.getParent(), jars, seen);
		}

		if (loader instanceof URLClassLoader) {
			for (URL url : ((URLClassLoader) loader).getURLs()) {
				addJarURL(jars, url, seen);
			}
		}
	}

	private static void addJarURL(List<File> jars, URL url, Set<File> seen) {
		String protocol = url.getProtocol();

		if ("file".equals(protocol)) {
			try {
				File file = new File(url.toURI());

				if (seen.add(file)) {
					if (file.isFile()) {
						String name = file.getName();

						if ((name.endsWith(".jar") || name.endsWith(".zip")) && (!jars.contains(file))) {
							jars.add(file);
						}
					} else if (file.isDirectory()) {
						jars.add(file);
					}
				}
			} catch (URISyntaxException e) {
				Trace.error(String.format("URL '%s' can't be translated into a local file", url), e);
			}
		}
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

	/**
	 * private inner class which must me loaded in the same class loder of parent
	 * class. The {@link ExtensionLink} allow this class to be forwarded to the
	 * 'child first' class loader.
	 * 
	 * @author rdesaintleger@axway.com
	 */
	@ExtensionLink(DynamicCompilerModule.class)
	private final static class CompilerListener implements DiagnosticListener<JavaFileObject> {
		@Override
		public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
			if (diagnostic != null) {
				Diagnostic.Kind kind = diagnostic.getKind();
				JavaFileObject source = diagnostic.getSource();
				String message = diagnostic.getMessage(null);

				switch (kind) {
				case ERROR:
				case WARNING:
				case MANDATORY_WARNING:
					/* report all warnings as errors */
					Trace.error(String.format("%s: %s line %d", source.getName(), kind.name(), diagnostic.getLineNumber()));
					Trace.error(message);
					break;
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
