package com.vordel.circuit.filter.devkit.context.tools;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.vordel.circuit.filter.devkit.context.annotations.ExtensionContext;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionLibraries;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionInstance;
import com.vordel.circuit.filter.devkit.script.extension.ScriptExtension;

/**
 * Annotation processor for FilterDevKit extensions.
 * 
 * This processor will generate registration files for annotated classes.
 * 
 * @author rdesaintleger@axway.com
 */
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@SupportedAnnotationTypes({ "com.vordel.circuit.filter.devkit.context.annotations.ExtensionContext",
		"com.vordel.circuit.filter.devkit.context.annotations.ExtensionInstance",
		"com.vordel.circuit.filter.devkit.script.extension.ScriptExtension", })
public class ExtensionProviderGenerator extends AbstractProcessor {
	/**
	 * Set of discovered classes
	 */
	private final Set<TypeElement> extensions = new HashSet<TypeElement>();

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		if (roundEnv.processingOver()) {
			try {
				/* no more rounds, write registration files */
				writeExtensionsFile();
			} catch (IOException e) {
				throw new IllegalStateException("Unable to write extensions files", e);
			}
		} else {
			/*
			 * retrieve all annotated components for this round and add them to registered
			 * classes
			 */
			Set<? extends Element> plugins = roundEnv.getElementsAnnotatedWith(ExtensionContext.class);
			Set<? extends Element> modules = roundEnv.getElementsAnnotatedWith(ExtensionInstance.class);
			Set<? extends Element> scripts = roundEnv.getElementsAnnotatedWith(ScriptExtension.class);

			registerClasses(plugins);
			registerClasses(modules);
			registerClasses(scripts);
		}

		return true;
	}

	/**
	 * Utility method to add classes in the registered set.
	 * 
	 * @param classes set of classes to be registered.
	 */
	private void registerClasses(Set<? extends Element> classes) {
		for (Element element : classes) {
			if (element instanceof TypeElement) {
				extensions.add((TypeElement) element);
			}
		}
	}

	/**
	 * Write registration files (list of registered class) and calls the external
	 * class path info writer.
	 * 
	 * @throws IOException if any error occurs
	 */
	private void writeExtensionsFile() throws IOException {
		if (!extensions.isEmpty()) {
			Filer filer = processingEnv.getFiler();
			FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/vordel/extensions");
			Writer services = file.openWriter();

			try {
				for (TypeElement element : extensions) {
					/*
					 * If we do have an extension library, create a library file as well. This will
					 * trigger a specific classloader for this module
					 */
					ExtensionLibraries libraries = element.getAnnotation(ExtensionLibraries.class);

					if (libraries != null) {
						writeLibrariesFile(filer, element, libraries.value());
					}

					services.append(String.format("%s\n", element.getQualifiedName().toString()));
				}
			} finally {
				services.close();
			}
		}
	}

	/**
	 * write class path info file for a given class.
	 * 
	 * @param filer   compiler standard filer.
	 * @param element annotated class
	 * @param entries list of class path selector expression
	 * @throws IOException if any error occurs
	 */
	private void writeLibrariesFile(Filer filer, TypeElement element, String[] entries) throws IOException {
		FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", String.format("META-INF/vordel/libraries/%s", element.getQualifiedName().toString()));
		Writer libraries = file.openWriter();

		try {
			for (String entry : entries) {
				libraries.append(String.format("%s\n", entry));
			}
		} finally {
			libraries.close();
		}
	}
}
