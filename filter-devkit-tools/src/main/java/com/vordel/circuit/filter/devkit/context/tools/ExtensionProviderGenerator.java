package com.vordel.circuit.filter.devkit.context.tools;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.vordel.circuit.filter.devkit.context.annotations.ExtensionContext;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionInstance;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionLibraries;
import com.vordel.circuit.filter.devkit.context.annotations.ExtensionLink;
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
	"com.vordel.circuit.filter.devkit.context.annotations.ExtensionLink",
	"com.vordel.circuit.filter.devkit.script.extension.ScriptExtension", })
public class ExtensionProviderGenerator extends AbstractProcessor {
	/**
	 * Set of discovered classes
	 */
	private final Set<TypeElement> extensions = new HashSet<TypeElement>();

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
			Set<? extends Element> plugins = roundEnv.getElementsAnnotatedWith(ExtensionContext.class);
			Set<? extends Element> modules = roundEnv.getElementsAnnotatedWith(ExtensionInstance.class);
			Set<? extends Element> scripts = roundEnv.getElementsAnnotatedWith(ScriptExtension.class);
			Set<? extends Element> links = roundEnv.getElementsAnnotatedWith(ExtensionLink.class);

			registerClasses(plugins);
			registerClasses(modules);
			registerClasses(scripts);
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
		Elements elements = processingEnv.getElementUtils();
		Types types = processingEnv.getTypeUtils();

		TypeElement typeElement = elements.getTypeElement(ExtensionLink.class.getCanonicalName());

		for (Element element : classes) {
			if (element instanceof TypeElement) {
				List<? extends AnnotationMirror> annotations = element.getAnnotationMirrors();

				for (AnnotationMirror annotation : annotations) {
					if (annotation.getAnnotationType().asElement().equals(typeElement)) {
						/* found a link annotation */
						for (Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotation.getElementValues().entrySet()) {
							if ("value".equals(entry.getKey().getSimpleName().toString())) {
								/* found the value method */
								AnnotationValue value = entry.getValue();

								if (value instanceof TypeMirror) {
									/* register found class */
									registerLink((TypeElement) element, (TypeElement) types.asElement((TypeMirror) value));
								} else {
									String className = value.toString();

									if (className.endsWith(".class")) {
										className = className.substring(0, className.lastIndexOf('.'));
									}

									/* register found class */
									registerLink((TypeElement) element, elements.getTypeElement(className));
								}
								break;
							}
						}

						break;
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
			Elements elements = processingEnv.getElementUtils();
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
						writeForceLoadFile(filer, elements, element, libraries.classes());
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

	/**
	 * write classes which needs to be loaded in the 'child first' class loader.
	 * 
	 * @param filer   compiler standard filer.
	 * @param element annotated class
	 * @param classes list of classes to be loaded
	 * @throws IOException if any error occurs
	 */
	private void writeForceLoadFile(Filer filer, Elements elements, TypeElement element, String[] classes) throws IOException {
		FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", String.format("META-INF/vordel/forceLoad/%s", element.getQualifiedName().toString()));
		Writer libraries = file.openWriter();

		Set<TypeElement> linked = links.get(element);
		Set<String> entries = new HashSet<String>();

		for (String entry : classes) {
			entries.add(entry);
		}

		if (linked != null) {
			for (TypeElement entry : linked) {
				Name name = elements.getBinaryName(entry);

				entries.add(name.toString());
			}
		}

		try {
			for (String entry : entries) {
				libraries.append(String.format("%s\n", entry));
			}
		} finally {
			libraries.close();
		}
	}
}
