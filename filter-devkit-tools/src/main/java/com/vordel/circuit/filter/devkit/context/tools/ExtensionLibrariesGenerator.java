package com.vordel.circuit.filter.devkit.context.tools;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.vordel.circuit.filter.devkit.context.annotations.ExtensionLibraries;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionLink;

/**
 * Annotation processor for FilterDevKit child first class loader.
 * 
 * This processor will generate libraries files for annotated classes.
 * 
 * @author rdesaintleger@axway.com
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes({ "com.vordel.circuit.filter.devkit.context.annotations.ExtensionLibraries",
"com.vordel.circuit.filter.devkit.context.annotations.ExtensionLink" })
public class ExtensionLibrariesGenerator extends AbstractProcessor {
	/**
	 * Set of discovered classes
	 */
	private final Set<TypeElement> libraries = new HashSet<TypeElement>();

	private final Map<TypeElement, Set<TypeElement>> links = new HashMap<TypeElement, Set<TypeElement>>();

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
			Set<? extends Element> libraries = roundEnv.getElementsAnnotatedWith(ExtensionLibraries.class);
			Set<? extends Element> links = roundEnv.getElementsAnnotatedWith(ExtensionLink.class);

			registerClasses(libraries);
			registerLinks(links);
		}

		return true;
	}

	/**
	 * process classloader links
	 * 
	 * @param classes set of classes to be processed.
	 */
	private void registerLinks(Set<? extends Element> classes) {
		Types types = processingEnv.getTypeUtils();

		for (Element element : classes) {
			if (element instanceof TypeElement) {
				AnnotationMirror annotation = AnnotationClassValueExtractor.getAnnotationMirror(element, ExtensionLink.class);

				if (annotation != null) {
					TypeMirror value = AnnotationClassValueExtractor.getAnnotationTypeMirrorValue(annotation, "value");

					if (value != null) {
						/* register found class */
						registerLink((TypeElement) element, (TypeElement) types.asElement((TypeMirror) value));
					}
				}
			}
		}
	}

	private void registerLink(TypeElement sibbling, TypeElement key) {
		if (key != null) {
			Set<TypeElement> linked = links.get(key);

			if (linked == null) {
				linked = new HashSet<TypeElement>();

				links.put(key, linked);
			}

			linked.add(sibbling);
		}
	}

	/**
	 * Utility method to add classes in the registered set.
	 * 
	 * @param classes set of classes to be registered.
	 */
	private void registerClasses(Set<? extends Element> classes) {
		for (Element element : classes) {
			if (element instanceof TypeElement) {
				libraries.add((TypeElement) element);
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
		if (!libraries.isEmpty()) {
			Filer filer = processingEnv.getFiler();
			Elements elements = processingEnv.getElementUtils();

			for (TypeElement element : libraries) {
				/*
				 * If we do have an extension library, create a library file as well. This will
				 * trigger a specific classloader for this module
				 */
				ExtensionLibraries annotation = element.getAnnotation(ExtensionLibraries.class);

				if (annotation != null) {
					writeLibrariesFile(filer, elements, element, annotation.value());
					writeForceLoadFile(filer, elements, element, annotation.classes());
				}
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
	private void writeLibrariesFile(Filer filer, Elements elements, TypeElement element, String[] entries) throws IOException {
		FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", String.format("META-INF/vordel/libraries/%s", elements.getBinaryName(element).toString()));
		Writer libraries = file.openWriter();

		try {
			for (String entry : entries) {
				libraries.append(String.format("%s\n", entry));
			}
		} finally {
			libraries.close();
		}
	}

	/**
	 * write classes which needs to be loaded in the 'child first' class loader.
	 * 
	 * @param filer   compiler standard filer.
	 * @param element annotated class
	 * @param classes list of classes to be loaded
	 * @throws IOException if any error occurs
	 */
	private void writeForceLoadFile(Filer filer, Elements elements, TypeElement element, String[] classes) throws IOException {
		FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", String.format("META-INF/vordel/forceLoad/%s", elements.getBinaryName(element).toString()));
		Writer libraries = file.openWriter();

		Set<TypeElement> linked = links.get(element);
		Set<String> entries = new HashSet<String>();

		Set<TypeElement> aggregated = new HashSet<TypeElement>();
		
		/* adds requested class */
		aggregateInnerTypes(aggregated, element);

		for (String entry : classes) {
			TypeElement entryType = null;

			try {
				/* try to lookup entry */
				String qualifiedName = entry.replace('$', '.');

				entryType = elements.getTypeElement(qualifiedName);
			} catch(RuntimeException e) {
				// ignore
			}

			if (entryType != null) {
				aggregateInnerTypes(aggregated, entryType);
			} else {
				entries.add(entry);
			}
		}

		if (linked != null) {
			for (TypeElement entry : linked) {
				aggregateInnerTypes(aggregated, entry);
			}
		}
		
		for (TypeElement entry : aggregated) {
			Name name = elements.getBinaryName(entry);

			entries.add(name.toString());
		}

		try {
			for (String entry : entries) {
				libraries.append(String.format("%s\n", entry));
			}
		} finally {
			libraries.close();
		}
	}

	private void aggregateInnerTypes(Set<TypeElement> aggregated, TypeElement element) {
		List<? extends Element> enclosed = element.getEnclosedElements();

		for(Element inner : enclosed) {
			if (inner instanceof TypeElement) {
				aggregateInnerTypes(aggregated, (TypeElement) inner);
			}
		}

		aggregated.add(element);
	}
}
