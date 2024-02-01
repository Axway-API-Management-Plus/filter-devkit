package com.vordel.circuit.ext.filter.quick;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class QuickFilterSupport {
	public static final String QUICKFILTER_DISPLAYNAME = "displayName";
	public static final String QUICKFILTER_DESCRIPTION = "description";
	public static final String QUICKFILTER_ICON = "icon";
	public static final String QUICKFILTER_PALETTE = "palette";
	public static final String QUICKFILTER_RESOURCES = "resources";
	public static final String QUICKFILTER_UI = "ui";
	public static final String QUICKFILTER_REQUIRED = "required";
	public static final String QUICKFILTER_CONSUMED = "consumed";
	public static final String QUICKFILTER_GENERATED = "generated";
	public static final String QUICKFILTER_ENGINENAME = "engineName";
	public static final String QUICKFILTER_SCRIPT = "script";

	public static final Set<String> QUICKFILTER_RESERVEDFIELDS = quickFilterReservedNames();

	private QuickFilterSupport() {
	}

	private static final Set<String> quickFilterReservedNames() {
		Set<String> reserved = new HashSet<String>();

		reserved.add("successNode");
		reserved.add("failureNode");
		reserved.add("name");
		reserved.add("logMask");
		reserved.add("logMaskType");
		reserved.add("logFatal");
		reserved.add("logFailure");
		reserved.add("logSuccess");
		reserved.add("category");
		reserved.add("abortProcessingOnLogError");
		reserved.add("classloader");

		reserved.add("class");

		reserved.add(QuickFilterSupport.QUICKFILTER_DISPLAYNAME);
		reserved.add(QuickFilterSupport.QUICKFILTER_DESCRIPTION);
		reserved.add(QuickFilterSupport.QUICKFILTER_ICON);
		reserved.add(QuickFilterSupport.QUICKFILTER_PALETTE);
		reserved.add(QuickFilterSupport.QUICKFILTER_RESOURCES);
		reserved.add(QuickFilterSupport.QUICKFILTER_ENGINENAME);
		reserved.add(QuickFilterSupport.QUICKFILTER_SCRIPT);
		reserved.add(QuickFilterSupport.QUICKFILTER_UI);

		reserved.add(QuickFilterSupport.QUICKFILTER_REQUIRED);
		reserved.add(QuickFilterSupport.QUICKFILTER_CONSUMED);
		reserved.add(QuickFilterSupport.QUICKFILTER_GENERATED);

		return Collections.unmodifiableSet(reserved);
	}

	public static void insertConstant(Element filter, String name, String... values) {
		if ((values != null) && (values.length > 0)) {
			Document document = filter.getOwnerDocument();
			StringBuilder builder = new StringBuilder();
	
			for(String value : values) {
				if (builder.length() > 0) {
					builder.append(',');
				}
	
				builder.append(value);
			}
	
			Element constantElement = document.createElement("constant");
	
			constantElement.setAttribute("name", name);
			constantElement.setAttribute("type", "string");
			constantElement.setAttribute("value", builder.toString());
	
			Node firstChild = filter.getFirstChild();
	
			if (firstChild == null) {
				filter.appendChild(constantElement);
			} else {
				filter.insertBefore(constantElement, firstChild);
			}
	
			filter.insertBefore(document.createTextNode("\n"), constantElement);
		}
	}

	public static String[] splitValues(String property, boolean trim) {
		List<String> values = new ArrayList<String>();
	
		if (property != null) {
			String[] array = property.split(",");
	
			for(String value : array) {
				if (trim) {
					value = value.trim();
				}
	
				if (!value.isEmpty()) {
					values.add(value);
				}
			}
		}
	
		return values.isEmpty() ? null : values.toArray(new String[0]);
	}

	public static String toString(Document document) {
		try {
			Source source = new DOMSource(document.getDocumentElement());
			StringWriter output = new StringWriter();
			Result result = new StreamResult(output);
	
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
	
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty(OutputKeys.INDENT, "no");
	
			transformer.transform(source, result);
	
			return output.toString();
		} catch (TransformerException e) {
			throw new IllegalStateException(e);
		}
	}

	public static String toString(Properties resources) {
		StringWriter builder = new StringWriter();
	
		try {
			resources.store(builder, null);
		} catch (IOException e) {
			/* ignore */
		}
	
		return builder.toString();
	}
}
