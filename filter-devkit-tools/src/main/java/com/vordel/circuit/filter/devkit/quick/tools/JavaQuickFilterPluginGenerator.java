package com.vordel.circuit.filter.devkit.quick.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import org.w3c.dom.Document;

import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterComponent;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterConsumed;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterField;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterGenerated;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterRequired;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterType;

@SupportedOptions({ "projectName", "projectExportedPackage" })
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes({ "com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterType" })
public class JavaQuickFilterPluginGenerator extends AbstractProcessor {
	private final Map<String, AnnotatedQuickFilter> filters = new HashMap<String, AnnotatedQuickFilter>();

	public JavaQuickFilterPluginGenerator() {
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		if (roundEnv.processingOver()) {
			generateFilterPackage(roundEnv);
			generateTypeSets(roundEnv);
		} else {
			processAnnotations(annotations, roundEnv);
		}
		return false;
	}

	private void generateTypeSets(RoundEnvironment roundEnv) {
		Map<String, AnnotatedTypeDoc> typesets = new HashMap<String, AnnotatedTypeDoc>();
		Filer filer = processingEnv.getFiler();

		try {
			List<AnnotatedTypeDoc> typedocs = new ArrayList<AnnotatedTypeDoc>();

			for (AnnotatedQuickFilter filter : filters.values()) {
				AnnotatedTypeDoc typedoc = getAnnotatedTypeSet(typesets, filter.filterDefinition);
				String typedocName = typedoc.getTypeDocName();

				FileObject typedocFile = filer.createResource(StandardLocation.CLASS_OUTPUT, "", String.format("typeset/%s", typedocName));

				typedoc.generateTypeDoc(typedocFile.openOutputStream(), filter);
				typedocs.add(typedoc);
			}

			FileObject typesetFile = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "typeset/typeset.xml");

			AbstractAnnotatedTypeDoc.generateTypeSet(typesetFile.openOutputStream(), typedocs);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to write typeset files", e);
		}
	}

	private static AnnotatedTypeDoc getAnnotatedTypeSet(Map<String, AnnotatedTypeDoc> typesets, TypeElement filterDefinition) {
		String entityTypeName = getEntityTypeName(filterDefinition);
		AnnotatedTypeDoc result = null;

		if (entityTypeName != null) {
			result = typesets.get(entityTypeName);

			if (result == null) {
				TypeElement superDefinition = getSuperDefinition(filterDefinition);
				AnnotatedTypeDoc superSet = null;

				if (superDefinition != null) {
					superSet = getAnnotatedTypeSet(typesets, superDefinition);
				}

				result = new AnnotatedTypeDoc(filterDefinition, superSet);
				typesets.put(entityTypeName, result);
			}
		}

		return result;
	}

	private static TypeElement getSuperDefinition(TypeElement filterDefinition) {
		TypeMirror superMirror = filterDefinition.getSuperclass();
		TypeElement result = null;

		if ((superMirror != null) && (superMirror.getKind() == TypeKind.DECLARED)) {
			DeclaredType superDeclaredType = (DeclaredType) superMirror;
			TypeElement superElement = (TypeElement) superDeclaredType.asElement();
			QuickFilterType annotation = superElement.getAnnotation(QuickFilterType.class);

			result = annotation == null ? getSuperDefinition(superElement) : superElement;
		}

		return result;
	}

	private void processAnnotations(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		Set<? extends Element> filterTypes = roundEnv.getElementsAnnotatedWith(QuickFilterType.class);
		Elements elementUtils = processingEnv.getElementUtils();
		Messager messager = processingEnv.getMessager();

		for (Element element : filterTypes) {
			/* assume the the definition is a class */
			TypeElement filterDefinition = (TypeElement) element;

			if (element.getKind() == ElementKind.INTERFACE) {
				messager.printMessage(Diagnostic.Kind.ERROR, "Annotation QuickFilterType cannot be applied to interfaces", element);
			} else {
				PackageElement packageElement = elementUtils.getPackageOf(filterDefinition);
				AnnotatedQuickFilter filter = new AnnotatedQuickFilter(filterDefinition, packageElement);

				filters.put(filter.getDefinitionQualifiedName(), filter);

				if (filter.isInstantiable()) {
					generateFilterFile(roundEnv, filter);
					generateGUIFile(roundEnv, filter);
					generatePageFile(roundEnv, filter);
					generateResourcesFile(roundEnv, filter);
					generateProcessorFile(roundEnv, filter);
				}
			}
		}
	}

	private void generateFilterPackage(RoundEnvironment roundEnv) {
		String projectName = getProjectName();
		Properties osgiResources = new Properties();
		Document plugin = AbstractJavaQuickFilterDefinition.createEclipsePlugin(projectName, projectName, filters.values());

		for (AnnotatedQuickFilter filter : filters.values()) {
			Properties resources = getFilterResources(roundEnv, filter);
			String displayName = resources.getProperty("FILTER_DISPLAYNAME");

			osgiResources.put(filter.getDisplayName().substring(1), displayName);
		}

		Filer filer = processingEnv.getFiler();

		try {
			FileObject bundleProperties = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "OSGI-INF/l10n/bundle.properties");
			FileObject pluginXML = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "plugin.xml");

			osgiResources.store(bundleProperties.openOutputStream(), "Autogenerated file. DO NOT EDIT");
			AbstractJavaQuickFilterDefinition.writeXml(pluginXML.openOutputStream(), plugin, false);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to write output files", e);
		}
	}

	private void generateFilterFile(RoundEnvironment roundEnv, AnnotatedQuickFilter filter) {
		try {
			String template = filter.generateJavaTemplate("filter_template.txt");
			String qualifiedName = filter.getFilterQualifiedName();

			template = template.replace("<requiredAttributes>", generateAttributeList(filter.filterDefinition.getAnnotation(QuickFilterRequired.class), (annotation) -> annotation.value()));
			template = template.replace("<consumedAttributes>", generateAttributeList(filter.filterDefinition.getAnnotation(QuickFilterConsumed.class), (annotation) -> annotation.value()));
			template = template.replace("<generatedAttributes>", generateAttributeList(filter.filterDefinition.getAnnotation(QuickFilterGenerated.class), (annotation) -> annotation.value()));

			writeTemplate(qualifiedName, template);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to write output filter template", e);
		}
	}

	private void generateProcessorFile(RoundEnvironment roundEnv, AnnotatedQuickFilter filter) {
		try {
			String template = filter.generateJavaTemplate("filter_processor_template.txt");
			String qualifiedName = filter.getProcessorQualifiedName();

			writeTemplate(qualifiedName, template);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to write output processor template", e);
		}
	}

	private void generateGUIFile(RoundEnvironment roundEnv, AnnotatedQuickFilter filter) {
		try {
			String template = filter.generateJavaTemplate("filter_gui_template.txt");
			String qualifiedName = filter.getGUIFilterQualifiedName();

			writeTemplate(qualifiedName, template);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to write output gui template", e);
		}
	}

	private void generatePageFile(RoundEnvironment roundEnv, AnnotatedQuickFilter filter) {
		try {
			String template = filter.generateJavaTemplate("filter_page_template.txt");
			String qualifiedName = filter.getPageQualifiedName();

			writeTemplate(qualifiedName, template);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to write output page template", e);
		}
	}

	private void generateResourcesFile(RoundEnvironment roundEnv, AnnotatedQuickFilter filter) {
		try {
			String template = filter.generateJavaTemplate("filter_resources_template.txt");
			String qualifiedName = filter.getResourcesQualifiedName();

			writeTemplate(qualifiedName, template);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to write output resources template", e);
		}
	}

	private void writeTemplate(String qualifiedName, String template) throws IOException {
		Filer filer = processingEnv.getFiler();

		JavaFileObject sourceFile = filer.createSourceFile(qualifiedName);
		Writer writer = sourceFile.openWriter();

		try {
			writer.append(template);
		} finally {
			writer.close();
		}
	}

	private static <T> String generateAttributeList(T holder, Function<T, String[]> getter) {
		StringBuilder builder = new StringBuilder();
		int count = 0;

		if (holder != null) {

			for (String value : getter.apply(holder)) {
				if (count > 0) {
					builder.append(", ");
				}

				builder.append(String.format("\"%s\"", AnnotatedQuickFilter.toStringLiteral(value)));
				count++;
			}
		}

		return count > 1 ? String.format("{ %s }", builder.toString()) : builder.toString();
	}

	public final String getProjectName() {
		String projectName = processingEnv.getOptions().get("projectName");

		if (projectName == null) {
			throw new IllegalStateException("Missing Project Name please use compiler option -AprojectName=<projectName>");
		}

		return projectName;
	}

	public final String getProjectExportedPackage() {
		String projectExportedPackage = processingEnv.getOptions().get("projectExportedPackage");

		if (projectExportedPackage == null) {
			throw new IllegalStateException("Missing Project exported package please use compiler option -AprojectExportedPackage=<project exported package>");
		}

		return projectExportedPackage;
	}

	public final Properties getFilterResources(RoundEnvironment roundEnv, AnnotatedQuickFilter filter) {
		Filer filer = processingEnv.getFiler();
		String packageName = filter.getDefinitionPackageName();
		String path = filter.getResourcesPath();

		Properties properties = new Properties();

		try {
			FileObject resource = filer.getResource(StandardLocation.SOURCE_PATH, packageName, path);
			InputStream in = resource.openInputStream();

			try {
				properties.load(in);
			} finally {
				in.close();
			}
		} catch (Exception e) {
			throw new IllegalStateException(String.format("Unable to read file %s relative to package %s", path, packageName), e);
		}

		return properties;
	}

	public static class AnnotatedTypeDoc extends AbstractAnnotatedTypeDoc {
		private final AnnotatedTypeDoc superDefinition;
		private final TypeElement filterDefinition;

		private final Map<String, QuickFilterComponent> components = new HashMap<String, QuickFilterComponent>();
		private final Map<String, QuickFilterField> fields = new HashMap<String, QuickFilterField>();

		private AnnotatedTypeDoc(TypeElement filterDefinition, AnnotatedTypeDoc superDefinition) {
			this.filterDefinition = filterDefinition;
			this.superDefinition = superDefinition;

			/* scan for components */
			for (ExecutableElement method : scanMethods(filterDefinition, new ArrayList<ExecutableElement>(), QuickFilterComponent.class)) {
				QuickFilterComponent component = method.getAnnotation(QuickFilterComponent.class);

				components.put(component.name(), component);
			}

			/* scan for fields */
			for (ExecutableElement method : scanMethods(filterDefinition, new ArrayList<ExecutableElement>(), QuickFilterField.class)) {
				QuickFilterField field = method.getAnnotation(QuickFilterField.class);

				fields.put(field.name(), field);
			}

			if (superDefinition != null) {
				/* exclude inherited components and fields */
				components.keySet().removeAll(superDefinition.components.keySet());
				fields.keySet().removeAll(superDefinition.fields.keySet());
			}
		}

		private List<ExecutableElement> scanMethods(TypeElement typeElement, List<ExecutableElement> annotatedMethods, Class<? extends Annotation> annotationClass) {
			// Get superclass
			TypeMirror superClass = typeElement.getSuperclass();
			if (superClass.getKind() == TypeKind.DECLARED) {
				TypeElement superElement = (TypeElement) ((DeclaredType) superClass).asElement();
				scanMethods(superElement, annotatedMethods, annotationClass);
			}

			// Get interfaces
			for (TypeMirror interfaceType : typeElement.getInterfaces()) {
				if (interfaceType.getKind() == TypeKind.DECLARED) {
					TypeElement interfaceElement = (TypeElement) ((DeclaredType) interfaceType).asElement();
					scanMethods(interfaceElement, annotatedMethods, annotationClass);
				}
			}

			// Scan methods
			for (Element enclosedElement : typeElement.getEnclosedElements()) {
				if (enclosedElement.getKind() == ElementKind.METHOD) {
					ExecutableElement method = (ExecutableElement) enclosedElement;
					if (method.getAnnotation(annotationClass) != null) {
						annotatedMethods.add(method);
					}
				}
			}

			return annotatedMethods;
		}

		@Override
		protected String getEntityTypeName() {
			return JavaQuickFilterPluginGenerator.getEntityTypeName(filterDefinition);
		}

		@Override
		protected String getSuperEntityTypeName() {
			return superDefinition == null ? "Filter" : superDefinition.getEntityTypeName();
		}

		@Override
		protected int getEntityVersion() {
			QuickFilterType annotation = filterDefinition.getAnnotation(QuickFilterType.class);

			return annotation.version();
		}

		@Override
		protected Iterable<QuickFilterComponent> getEntityComponents() {
			return components.values();
		}

		@Override
		protected Iterable<QuickFilterField> getEntityFields() {
			return fields.values();
		}
	}

	public class AnnotatedQuickFilter extends AbstractJavaQuickFilterDefinition {
		private final TypeElement filterDefinition;
		private final PackageElement packageElement;

		private AnnotatedQuickFilter(TypeElement filterDefinition, PackageElement packageElement) {
			this.filterDefinition = filterDefinition;
			this.packageElement = packageElement;
		}

		@Override
		public boolean isInstantiable() {
			if (filterDefinition.getKind() == ElementKind.CLASS) {
				Set<Modifier> modifiers = filterDefinition.getModifiers();

				return !modifiers.contains(Modifier.ABSTRACT);
			}

			return false;
		}

		@Override
		public String getDisplayName() {
			return String.format("%%%s_TYPE_NAME", getEntityTypeName(filterDefinition).toUpperCase());
		}

		@Override
		public String getCategory() {
			QuickFilterType annotation = filterDefinition.getAnnotation(QuickFilterType.class);
			String category = annotation.category();

			return getCategory(category);
		}

		@Override
		public String getIcon() {
			QuickFilterType annotation = filterDefinition.getAnnotation(QuickFilterType.class);
			String icon = annotation.icon();

			if ((icon == null) || icon.isEmpty()) {
				/* default value for icon */
				icon = "filter_small";
			}

			return icon;
		}

		public String getResourcesPath() {
			QuickFilterType annotation = filterDefinition.getAnnotation(QuickFilterType.class);
			String resources = annotation.resources();

			if ((resources == null) || resources.isEmpty()) {
				throw new IllegalStateException("Resources file %s missing from annotation");
			}

			return resources;
		}

		@Override
		public String getUIFilePath() {
			QuickFilterType annotation = filterDefinition.getAnnotation(QuickFilterType.class);
			String page = annotation.page();

			if ((page == null) || page.isEmpty()) {
				throw new IllegalStateException("page file %s missing from annotation");
			}

			return page;
		}

		@Override
		public String getDefinitionPackageName() {
			return packageElement.toString();
		}

		@Override
		protected String getExportedPackageName() {
			return getProjectExportedPackage();
		}

		@Override
		protected String getDefinitionSimpleName() {
			return filterDefinition.getSimpleName().toString();
		}

		@Override
		protected String getDefinitionQualifiedName() {
			return filterDefinition.getQualifiedName().toString();
		}
	}

	private static String getEntityTypeName(TypeElement filterDefinition) {
		QuickFilterType annotation = filterDefinition.getAnnotation(QuickFilterType.class);
		String typeName = annotation.name();

		if ((typeName == null) || typeName.isEmpty()) {
			typeName = filterDefinition.getSimpleName().toString();
		}

		return typeName;
	}
}
