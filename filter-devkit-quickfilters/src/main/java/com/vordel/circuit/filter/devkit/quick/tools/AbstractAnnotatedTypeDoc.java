package com.vordel.circuit.filter.devkit.quick.tools;

import java.io.OutputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterComponent;
import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterField;

public abstract class AbstractAnnotatedTypeDoc {
	public static void generateTypeSet(OutputStream out, Iterable<? extends AbstractAnnotatedTypeDoc> typedocs) {
		Document document = AbstractJavaQuickFilterDefinition.createDocument();
		Element typeset = document.createElement("typeSet");
		
		for(AbstractAnnotatedTypeDoc typedoc : typedocs) {
			Element element = document.createElement("typedoc");
			
			element.setAttribute("file", typedoc.getTypeDocName());
			typeset.appendChild(element);
		}
		
		document.appendChild(typeset);
		
		AbstractJavaQuickFilterDefinition.writeXml(out, document, true);
	}
	public void generateTypeDoc(OutputStream out, AbstractJavaQuickFilterDefinition definition) {
		String entityTypeName = getEntityTypeName();
		String superTypeName = getSuperEntityTypeName();
		Document document = AbstractJavaQuickFilterDefinition.createDocument();
		Element data = document.createElement("entityStoreData");
		Element filter = document.createElement("entityType");

		filter.setAttribute("extends", superTypeName);
		filter.setAttribute("name", entityTypeName);

		appendEntityConstant(filter, "_version", "integer", String.valueOf(getEntityVersion()));

		if (definition.isInstantiable()) {
			appendEntityConstant(filter, "class", "string", definition.getFilterQualifiedName());
		}

		for(QuickFilterComponent component : getEntityComponents()) {
			appendEntityComponent(filter, component);
		}

		for(QuickFilterField field : getEntityFields()) {
			appendEntityField(filter, field);
		}

		data.appendChild(filter);
		document.appendChild(data);
		
		AbstractJavaQuickFilterDefinition.writeXml(out, document, true);
	}

	private static void appendEntityConstant(Element entity, String name, String type, String value) {
		Document document = entity.getOwnerDocument();
		Element constant = document.createElement("constant");

		constant.setAttribute("name", name);
		constant.setAttribute("type", type);
		constant.setAttribute("value", value);

		entity.appendChild(constant);
	}

	private static void appendEntityComponent(Element entity, QuickFilterComponent component) {
		Document document = entity.getOwnerDocument();
		Element element = document.createElement("componentType");

		element.setAttribute("cardinality", component.cardinality());
		element.setAttribute("name", component.name());

		entity.appendChild(element);
	}

	private static void appendEntityField(Element entity, QuickFilterField field) {
		Document document = entity.getOwnerDocument();
		Element element = document.createElement("field");
		String[] defaults = field.defaults();

		element.setAttribute("cardinality", field.cardinality());
		element.setAttribute("name", field.name());
		element.setAttribute("type", field.type());

		if (defaults.length == 1) {
			element.setAttribute("default", defaults[0]);
		} else if (defaults.length > 1) {
			for (String defaultValue : defaults) {
				Element defaultValueElement = document.createElement("defaultValue");

				defaultValueElement.appendChild(document.createTextNode(defaultValue));
				element.appendChild(defaultValueElement);
			}
		}

		entity.appendChild(element);
	}
	
	public String getTypeDocName() {
		return String.format("%s.xml", getEntityTypeName().toLowerCase());
	}
	
	protected abstract String getEntityTypeName();

	protected abstract String getSuperEntityTypeName();

	protected abstract int getEntityVersion();

	protected abstract Iterable<QuickFilterComponent> getEntityComponents();

	protected abstract Iterable<QuickFilterField> getEntityFields();
}
