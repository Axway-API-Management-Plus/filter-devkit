package com.vordel.circuit.filter.devkit.context.tools;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map.Entry;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

public class AnnotationClassValueExtractor<T> extends SimpleAnnotationValueVisitor8<TypeMirror, T> {
	@Override
	public TypeMirror visitType(TypeMirror t, T p) {
		return t;
	}

	public static AnnotationMirror getAnnotationMirror(Element element, Class<? extends Annotation> clazz) {
		String typeName = clazz.getCanonicalName();
		List<? extends AnnotationMirror> annotations = element.getAnnotationMirrors();

		for (AnnotationMirror annotation : annotations) {
			TypeElement typeElement = (TypeElement) annotation.getAnnotationType().asElement();

			if (typeElement.getQualifiedName().toString().equals(typeName)) {
				return annotation;
			}
		}

		return null;
	}

	public static AnnotationValue getAnnotationValue(AnnotationMirror annotation, String key) {
		for (Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotation.getElementValues().entrySet()) {
			if (entry.getKey().getSimpleName().toString().equals(key)) {
				return entry.getValue();
			}
		}

		return null;
	}

	public static TypeMirror getAnnotationTypeMirrorValue(AnnotationMirror annotation, String key) {
		AnnotationValue value = getAnnotationValue(annotation, key);
		AnnotationClassValueExtractor<Void> visitor = new AnnotationClassValueExtractor<Void>();

		return value.accept(visitor, null);
	}
}
