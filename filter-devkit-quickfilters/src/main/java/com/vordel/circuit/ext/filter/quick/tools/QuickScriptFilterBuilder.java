package com.vordel.circuit.ext.filter.quick.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.vordel.circuit.ext.filter.quick.QuickFilterSupport;

public class QuickScriptFilterBuilder {
	private static final Pattern SCRIPTNAME_REGEX = Pattern.compile("script\\.(js|py|groovy)");

	private static Map<String, String> ENGINENAMES = engineNameMap();

	private static void printUsage(String message) {
		if (message != null) {
			System.err.println(message);
		}

		System.err.println("Usage: QuickScriptFilterBuilder <filter directory name>");
		System.exit(-1);
	}

	public static void main(String[] args) {
		if (args.length != 1) {
			printUsage("wrong number of arguments");
		}

		File root = new File(args[0]);

		if (!root.isDirectory()) {
			printUsage("The filter argument is not a directory");
		}

		File ui = new File(root, "ui.xml");

		if (!ui.isFile()) {
			printUsage("ui.xml file is missing");
		}

		File resources = new File(root, "resources.properties");

		if (!resources.isFile()) {
			printUsage("resources.properties file is missing");
		}

		File typeset = new File(root, "typedoc.xml");

		if (!typeset.isFile()) {
			printUsage("typedoc.xml file is missing");
		}

		File script = locateScriptFile(root);
		Matcher matcher = SCRIPTNAME_REGEX.matcher(script.getName());

		if (!matcher.matches()) {
			/* This should not occur */
			throw new IllegalStateException();
		}

		String extension = matcher.group(1);
		String engineName = ENGINENAMES.get(extension);

		if (engineName == null) {
			printUsage("no registered engine for the given script");
		}

		Properties props = new Properties();

		try {
			props.load(new FileInputStream(resources));

			String name = props.getProperty("FILTER_DISPLAYNAME", null);
			String description = props.getProperty("FILTER_DESCRIPTION", null);

			if ((name == null) || name.isEmpty()) {
				printUsage("filter display Name can't be null or empty");
			}

			if ((description == null) || description.isEmpty()) {
				printUsage("filter description can't be null or empty");
			}

			buildQuickFilter(root, name, description, engineName, script, ui, typeset, props);
		} catch (IOException e) {
			printUsage("Unable to load filter resources");
		}
	}

	private static void buildQuickFilter(File root, String name, String description, String engineName, File script, File ui, File typedoc, Properties props) {
		File build = new File(root, "build");

		if ((!build.exists()) && (!build.mkdirs())) {
			printUsage("can't create build directory");
		}

		/* parse and update entity type with constants */
		Element type = parseQuickFilterType(name, description, engineName, script, ui, typedoc, props);

		writeXml(typedoc = new File(build, "typedoc.xml"), type.getOwnerDocument());
		createTypeSet(typedoc);
	}

	private static void writeXml(File out, Document document) {
		try {
			Source source = new DOMSource(document.getDocumentElement());
			OutputStream output = new FileOutputStream(out);
			Result result = new StreamResult(output);

			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();

			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty(OutputKeys.INDENT, "no");

			transformer.transform(source, result);
		} catch (TransformerException e) {
			throw new IllegalStateException(e);
		} catch (IOException e) {
			printUsage(String.format("can't write file '%s'", out.getName()));
		}
	}

	private static File createTypeSet(File typedoc) {		
		File typeset = new File(typedoc.getParent(), "typeset.xml");

		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

			factory.setNamespaceAware(true);

			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.newDocument();

			Element typesetNode = document.createElement("typeSet");
			Element typedocNode = document.createElement("typedoc");

			typedocNode.setAttribute("file", typedoc.getName());

			typesetNode.appendChild(typedocNode);
			document.appendChild(typesetNode);

			writeXml(typeset, document);
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException(e);
		}

		return typeset;
	}

	private static Element parseQuickFilterType(String name, String description, String engineName, File script, File ui, File typedoc, Properties props) {
		Document document = parseXml(typedoc);
		Element root = document.getDocumentElement();

		if ((root == null) || (!"entityStoreData".equals(root.getTagName()))) {
			printUsage("typeset root element is invalid (expected <entityStoreData>)");
		}

		List<Element> quickFilters = new ArrayList<Element>();

		NodeList types = root.getElementsByTagName("entityType");
		int typesLength = types.getLength();

		for (int typeIndex = 0; typeIndex < typesLength; typeIndex++) {
			Element type = (Element) types.item(typeIndex);
			String extend = type.getAttribute("extends");

			if (!extend.equals("Filter")) {
				printUsage("filter type must extend 'Filter'");
			} else {
				quickFilters.add(type);

				NodeList fields = type.getElementsByTagName("field");
				int fieldsLength = fields.getLength();

				for (int fieldIndex = 0; fieldIndex < fieldsLength; fieldIndex++) {
					Element field = (Element) fields.item(fieldIndex);
					String fieldName = field.getAttribute("name");

					if (QuickFilterSupport.QUICKFILTER_RESERVEDFIELDS.contains(fieldName)) {
						printUsage(String.format("'%s' is a reserved field name", fieldName));
					}
				}

				NodeList constants = type.getElementsByTagName("constant");
				int constantsLength = constants.getLength();

				for (int constantIndex = 0; constantIndex < constantsLength; constantIndex++) {
					Element constant = (Element) constants.item(constantIndex);
					String constantName = constant.getAttribute("name");

					if (QuickFilterSupport.QUICKFILTER_RESERVEDFIELDS.contains(constantName)) {
						printUsage(String.format("'%s' is a reserved constant name", constantName));
					}
				}
			}
		}

		if (quickFilters.isEmpty()) {
			printUsage("no quick filter declaration");
		} else if (quickFilters.size() > 1) {
			printUsage("multiple quick filter declaration");
		}

		Element filter = quickFilters.get(0);
		//String[] categories = QuickScriptFilter.splitValues(props.getProperty("FILTER_CATEGORIES", "Utility"), true);

		/*
		 * populate filter constants
		 */
		QuickFilterSupport.insertConstant(filter, QuickFilterSupport.QUICKFILTER_UI, QuickFilterSupport.toString(parseXml(ui)));
		QuickFilterSupport.insertConstant(filter, QuickFilterSupport.QUICKFILTER_SCRIPT, parseScript(script));
		QuickFilterSupport.insertConstant(filter, QuickFilterSupport.QUICKFILTER_RESOURCES, QuickFilterSupport.toString(props));
		QuickFilterSupport.insertConstant(filter, QuickFilterSupport.QUICKFILTER_GENERATED, QuickFilterSupport.splitValues(props.getProperty("QUICKFILTER_GENERATED", null), true));
		QuickFilterSupport.insertConstant(filter, QuickFilterSupport.QUICKFILTER_CONSUMED, QuickFilterSupport.splitValues(props.getProperty("QUICKFILTER_CONSUMED", null), true));
		QuickFilterSupport.insertConstant(filter, QuickFilterSupport.QUICKFILTER_REQUIRED, QuickFilterSupport.splitValues(props.getProperty("QUICKFILTER_REQUIRED", null), true));
		QuickFilterSupport.insertConstant(filter, QuickFilterSupport.QUICKFILTER_PALETTE, QuickFilterSupport.splitValues(props.getProperty("FILTER_CATEGORY", "Utility"), true));
		QuickFilterSupport.insertConstant(filter, QuickFilterSupport.QUICKFILTER_ICON, props.getProperty("FILTER_ICON", "filter_small"));
		QuickFilterSupport.insertConstant(filter, QuickFilterSupport.QUICKFILTER_ENGINENAME, engineName);
		QuickFilterSupport.insertConstant(filter, QuickFilterSupport.QUICKFILTER_DESCRIPTION, description);
		QuickFilterSupport.insertConstant(filter, QuickFilterSupport.QUICKFILTER_DISPLAYNAME, name);
		QuickFilterSupport.insertConstant(filter, "class", "com.vordel.circuit.ext.filter.quick.QuickScriptFilter");

		return filter;
	}

	private static File locateScriptFile(File root) {
		List<File> scripts = new ArrayList<File>();

		for (File script : root.listFiles()) {
			if (script.isFile() && SCRIPTNAME_REGEX.matcher(script.getName()).matches()) {
				scripts.add(script);
			}
		}

		int count = scripts.size();

		if (count == 0) {
			printUsage("script is missing (one of script.js, script.py or script.groovy)");
		} else if (count > 1) {
			printUsage("multiple scripts found");
		}

		return scripts.get(0);
	}

	private static Map<String, String> engineNameMap() {
		Map<String, String> engineNameMap = new HashMap<String, String>();

		engineNameMap.put("js", "nashorn");
		engineNameMap.put("py", "jython");
		engineNameMap.put("groovy", "groovy");

		return Collections.unmodifiableMap(engineNameMap);
	}

	private static Document parseXml(File xml) {
		Document document = null;

		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

			factory.setNamespaceAware(true);

			DocumentBuilder builder = factory.newDocumentBuilder();

			document = builder.parse(xml);
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException(e);
		} catch (SAXException e) {
			printUsage(String.format("xml file '%s' can't be parsed", xml.getName()));
		} catch (IOException e) {
			printUsage(String.format("xml file '%s' can't be parsed", xml.getName()));
		}

		return document;
	}

	private static String parseScript(File script) {
		String result = null;

		try {
			RandomAccessFile raf = new RandomAccessFile(script, "r");

			try {
				long length = raf.length();

				if (length > ((long) Integer.MAX_VALUE)) {
					printUsage(String.format("script file '%s' is too long", script.getName()));
				}

				MappedByteBuffer map = raf.getChannel().map(MapMode.READ_ONLY, 0, length);
				byte[] buffer = new byte[(int) length];

				map.get(buffer);

				result = new String(buffer, "UTF-8");
			} finally {
				raf.close();
			}
		} catch (IOException e) {
			printUsage(String.format("script file '%s' can't be parsed", script.getName()));
		}

		return result;
	}
}
