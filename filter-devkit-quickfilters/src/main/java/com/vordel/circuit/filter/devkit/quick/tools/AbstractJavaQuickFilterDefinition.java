package com.vordel.circuit.filter.devkit.quick.tools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
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
import org.w3c.dom.ProcessingInstruction;

public abstract class AbstractJavaQuickFilterDefinition {
	private static Map<String, String> ECLIPSE_CATEGORY;

	static {
		ECLIPSE_CATEGORY = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);

		ECLIPSE_CATEGORY.put("Amazon Web Services", "com.vordel.ui.filter.category.amazon");
		ECLIPSE_CATEGORY.put("Attributes", "com.vordel.ui.filter.category.attributes");
		ECLIPSE_CATEGORY.put("Authentication", "com.vordel.ui.filter.category.authentication");
		ECLIPSE_CATEGORY.put("Authorization", "com.vordel.ui.filter.category.authorization");
		ECLIPSE_CATEGORY.put("Cache", "com.vordel.ui.filter.category.cache");
		ECLIPSE_CATEGORY.put("Certificate", "com.vordel.ui.filter.category.certificate");
		ECLIPSE_CATEGORY.put("Circuit Activation", "com.vordel.ui.filter.category.circuit_activation");
		ECLIPSE_CATEGORY.put("Content Filtering", "com.vordel.ui.filter.category.content_filtering");
		ECLIPSE_CATEGORY.put("Conversion", "com.vordel.ui.filter.category.conversion");
		ECLIPSE_CATEGORY.put("Encryption", "com.vordel.ui.filter.category.encryption");
		ECLIPSE_CATEGORY.put("Fault Handlers", "com.vordel.ui.filter.category.fault_handlers");
		ECLIPSE_CATEGORY.put("Integrity", "com.vordel.ui.filter.category.integrity");
		ECLIPSE_CATEGORY.put("Monitoring", "com.vordel.ui.filter.category.monitoring");
		ECLIPSE_CATEGORY.put("OAuth 2.0", "com.vordel.ui.filter.category.oauth2");
		ECLIPSE_CATEGORY.put("OAuth 2.0 Client", "com.vordel.ui.filter.category.oauth2_client");
		ECLIPSE_CATEGORY.put("Open ID Connect", "com.vordel.ui.filter.category.open_id_connect");
		ECLIPSE_CATEGORY.put("Oracle Access Manager", "com.vordel.ui.filter.category.oracleAccessManager");
		ECLIPSE_CATEGORY.put("Oracle Entitlements Server", "com.vordel.ui.filter.category.oracle_entitlements_server");
		ECLIPSE_CATEGORY.put("Resolver", "com.vordel.ui.filter.category.resolver");
		ECLIPSE_CATEGORY.put("Routing", "com.vordel.ui.filter.category.routing");
		ECLIPSE_CATEGORY.put("Security Services", "com.vordel.ui.filter.category.security_services");
		ECLIPSE_CATEGORY.put("Sun Access Manager", "com.vordel.ui.filter.category.sunAccessManager");
		ECLIPSE_CATEGORY.put("Trust", "com.vordel.ui.filter.category.trust");
		ECLIPSE_CATEGORY.put("Utility", "com.vordel.ui.filter.category.utility");
		ECLIPSE_CATEGORY.put("Web Service", "com.vordel.ui.filter.category.web_service");
	}

	public String generateJavaTemplate(String templateName) throws IOException {
		String template = readTemplate(AbstractJavaQuickFilterDefinition.class.getResourceAsStream(templateName));
		String generated = template;

		String uiFile = getUIFilePath();
		String resourcesFile = getResourcesPath().replace('/', '.');

		if (resourcesFile.endsWith(".properties")) {
			resourcesFile = resourcesFile.substring(0, resourcesFile.lastIndexOf('.'));
		}
		
		generated = generated.replace("<filterQualifiedName>", getFilterQualifiedName());
		generated = generated.replace("<guiQualifiedName>", getGUIFilterQualifiedName());
		generated = generated.replace("<pageQualifiedName>", getPageQualifiedName());
		generated = generated.replace("<resourcesQualifiedName>", getResourcesQualifiedName());
		generated = generated.replace("<processorQualifiedName>", getProcessorQualifiedName());
		generated = generated.replace("<definitionQualifiedName>", getDefinitionQualifiedName());

		generated = generated.replace("<packageName>", getDefinitionPackageName());
		generated = generated.replace("<exportedPackageName>", getExportedPackageName());

		generated = generated.replace("<filterSimpleName>", getFilterSimpleName());
		generated = generated.replace("<guiSimpleName>", getGUIFilterSimpleName());
		generated = generated.replace("<pageSimpleName>", getPageSimpleName());
		generated = generated.replace("<resourcesSimpleName>", getResourcesSimpleName());
		generated = generated.replace("<processorSimpleName>", getProcessorSimpleName());
		generated = generated.replace("<resourcesFile>", toStringLiteral(resourcesFile));
		generated = generated.replace("<uiFile>", toStringLiteral(uiFile));
		generated = generated.replace("<icon>", toStringLiteral(getIcon()));
		generated = generated.replace("<category>", getCategory());

		return generated;
	}

	public String getFilterQualifiedName() {
		return String.format("%s.%s", getExportedPackageName(), getFilterSimpleName());
	}

	public String getPageQualifiedName() {
		return String.format("%s.%s", getExportedPackageName(), getPageSimpleName());
	}

	public abstract boolean isInstantiable();

	public abstract String getDisplayName();

	public abstract String getCategory();

	public abstract String getIcon();

	protected abstract String getResourcesPath();

	protected abstract String getUIFilePath();

	protected abstract String getDefinitionPackageName();

	protected abstract String getExportedPackageName();

	protected abstract String getDefinitionSimpleName();

	protected abstract String getDefinitionQualifiedName();

	private String getDefinitionSimpleBaseName() {
		String baseName = getDefinitionSimpleName();

		if (baseName.endsWith("Filter")) {
			baseName = baseName.substring(0, baseName.lastIndexOf("Filter"));
		}

		return baseName;
	}

	public String getFilterSimpleName() {
		return String.format("%sFilter", getDefinitionSimpleBaseName());
	}

	public String getGUIFilterQualifiedName() {
		return String.format("%s.%s", getExportedPackageName(), getGUIFilterSimpleName());
	}

	public String getGUIFilterSimpleName() {
		return String.format("%sGUIFilter", getDefinitionSimpleBaseName());
	}

	public String getPageSimpleName() {
		return String.format("%sPage", getDefinitionSimpleBaseName());
	}

	public String getProcessorQualifiedName() {
		return String.format("%s.%s", getDefinitionPackageName(), getProcessorSimpleName());
	}

	public String getProcessorSimpleName() {
		return String.format("%sProcessor", getDefinitionSimpleBaseName());
	}

	public String getResourcesQualifiedName() {
		return String.format("%s.%s", getDefinitionPackageName(), getResourcesSimpleName());
	}

	public String getResourcesSimpleName() {
		return String.format("%sResources", getDefinitionSimpleBaseName());
	}

	private static final String readTemplate(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int count = 0;

		while ((count = in.read(buffer)) > -1) {
			out.write(buffer, 0, count);
		}

		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}

	protected static String toStringLiteral(String value) {
		String result = null;

		if (value != null) {
			char[] encoded = value.toCharArray();
			int length = 0;

			/* loop a first time against characters to compute literal max length */
			for (char c : encoded) {
				switch (c) {
				case '\b':
				case '\t':
				case '\n':
				case '\f':
				case '\r':
				case '"':
				case '\\':
					length += 2;
					break;
				case '\'':
					/* simple quote does not need escape for java string literal */
				default:
					if (Character.isISOControl(c)) {
						/*
						 * octal escapes use 4 characters versus 6 in unicode escape, this is why we use
						 * it
						 */
						length += 4;
					} else {
						length += 1;
					}
				}
			}

			StringBuilder builder = new StringBuilder(length);

			for (int index = 0; index < encoded.length; index++) {
				char c = encoded[index];

				switch (c) {
				case '\b':
					builder.append("\\b");
					break;
				case '\t':
					builder.append("\\t");
					break;
				case '\n':
					builder.append("\\n");
					break;
				case '\f':
					builder.append("\\f");
					break;
				case '\r':
					builder.append("\\r");
					break;
				case '"':
					builder.append("\\\"");
					break;
				case '\\':
					builder.append("\\\\");
					break;
				case '\'':
					/* simple quote does not need escape for java string literal */
				default:
					if (Character.isISOControl(c)) {
						int next = index + 1;

						/*
						 * octal escapes use at most 4 characters versus 6 in unicode escape, this is
						 * why we use it
						 */
						if ((next == encoded.length) || (!Character.isDigit(encoded[next]))) {
							/* use short escape */
							builder.append(String.format("\\%o", (int) c));
						} else {
							/* use long escape */
							builder.append(String.format("\\%03o", (int) c));
						}
					} else {
						/* regular case... we do not need escape */
						builder.append(c);
					}
				}
			}

			result = builder.toString();
		}

		return result;
	}

	protected static Document createDocument() {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

			factory.setNamespaceAware(true);

			DocumentBuilder builder = factory.newDocumentBuilder();
			return builder.newDocument();
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException(e);
		}
	}
	
	protected static String writeXmlAsString(Document document, boolean omitXml) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		
		writeXml(out, document, omitXml);
		
		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}

	protected static void writeXml(OutputStream output, Document document, boolean omitXml) {
		try {
			Source source = new DOMSource(document.getDocumentElement());
			Result result = new StreamResult(output);

			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();

			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, omitXml ? "yes" : "no");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");

			transformer.transform(source, result);
		} catch (TransformerException e) {
			throw new IllegalStateException(e);
		}
	}

	public static Document createEclipsePlugin(String projectID, String projectName, Collection<? extends AbstractJavaQuickFilterDefinition> annotatedFilters) {
		Document document = createDocument();

		ProcessingInstruction eclipse = document.createProcessingInstruction("eclipse", "version=\"3.4\"");
		Element extension = document.createElement("extension");
		Element plugin = document.createElement("plugin");

		document.appendChild(plugin);
		document.insertBefore(eclipse, plugin);
		plugin.appendChild(extension);

		extension.setAttribute("point", "com.vordel.rcp.filterbase.filterGUI");
		extension.setAttribute("id", String.format("com.vordel.ui.filterGUI.%s", projectID));
		extension.setAttribute("name", projectName);

		for (AbstractJavaQuickFilterDefinition item : annotatedFilters) {
			Element filter = document.createElement("filter");
			Element category = document.createElement("category");

			filter.setAttribute("class", item.getFilterQualifiedName());
			filter.setAttribute("icon", item.getIcon());
			filter.setAttribute("name", item.getDisplayName());
			filter.setAttribute("page", item.getPageQualifiedName());
			category.setAttribute("id", toEclipseCategory(item.getCategory()));

			extension.appendChild(filter);
			filter.appendChild(category);
		}

		return document;
	}

	public static String toEclipseCategory(String category) {
		String result = ECLIPSE_CATEGORY.get(category);

		if (result == null) {
			for (Entry<String, String> entry : ECLIPSE_CATEGORY.entrySet()) {
				if (category.equals(entry.getValue())) {
					result = category;
					break;
				}
			}
		}

		if (result == null) {
			result = "com.vordel.ui.filter.category.utility";
		}

		return result;
	}

	public static String getCategory(String category) {
		category = toEclipseCategory(category);

		for (Entry<String, String> entry : ECLIPSE_CATEGORY.entrySet()) {
			if (category.equals(entry.getValue())) {
				return entry.getKey();
			}
		}

		return "Utility";
	}
}
