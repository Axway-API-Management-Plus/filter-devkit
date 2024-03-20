package com.vordel.circuit.filter.devkit.context.tools;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

public class AnnotationClassArrayValueExtractor<T> extends SimpleAnnotationValueVisitor8<TypeMirror[], T> {

	@Override
	public TypeMirror[] visitArray(List<? extends AnnotationValue> vals, T p) {
		List<TypeMirror> extracted = new ArrayList<TypeMirror>();
		AnnotationClassValueExtractor<Void> visitor = new AnnotationClassValueExtractor<Void>();

		for (AnnotationValue value : vals) {
			TypeMirror mirror = value.accept(visitor, null);

			if (mirror != null) {
				extracted.add(mirror);
			}
		}

		return extracted.toArray(new TypeMirror[0]);
	}

	public static TypeMirror[] getAnnotationTypeMirrorArrayValue(AnnotationMirror annotation, String key) {
		AnnotationValue value = AnnotationClassValueExtractor.getAnnotationValue(annotation, key);

		if (value != null) {
			AnnotationClassArrayValueExtractor<Void> visitor = new AnnotationClassArrayValueExtractor<Void>();

			return value.accept(visitor, null);
		}

		return null;
	}
}
