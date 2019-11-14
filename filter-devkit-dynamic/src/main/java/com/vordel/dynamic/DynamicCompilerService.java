package com.vordel.dynamic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
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
import org.xml.sax.SAXException;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.ext.filter.quick.QuickFilterField;
import com.vordel.circuit.ext.filter.quick.QuickFilterSupport;
import com.vordel.circuit.ext.filter.quick.QuickFilterType;
import com.vordel.circuit.ext.filter.quick.QuickJavaFilter;
import com.vordel.circuit.ext.filter.quick.QuickScriptFilterBuilder;
import com.vordel.circuit.jaxrs.VordelBodyProvider;
import com.vordel.circuit.script.bind.ExtensionModule;
import com.vordel.circuit.script.bind.ExtensionPlugin;
import com.vordel.circuit.script.bind.ExtensionScanner;
import com.vordel.circuit.script.bind.InvocableMethod;
import com.vordel.circuit.script.jaxrs.ScriptWebComponent;
import com.vordel.config.ConfigContext;
import com.vordel.es.EntityStoreException;
import com.vordel.mime.Body;
import com.vordel.mime.ContentType;
import com.vordel.mime.HeaderSet;
import com.vordel.trace.Trace;

@ExtensionPlugin("dynamic.compiler")
public class DynamicCompilerService implements ExtensionModule {
	private static final String MEDIATYPE_ZIP = "application/zip";
	private final ScriptWebComponent JAXRS_COMPONENT = ScriptWebComponent.createWebComponent(this);

	@Override
	public void attachModule(ConfigContext ctx) throws EntityStoreException {
		/*
		 * nothing to do for attach... ExtensionModule interface is needed for JAX-RS
		 * instance binding
		 */
	}

	@Override
	public void detachModule() {
		/*
		 * nothing to do for detach... ExtensionModule interface is needed for JAX-RS
		 * instance binding
		 */
	}

	@InvocableMethod("TypeSetService")
	public boolean service(Message msg) throws CircuitAbortException {
		/* just call the JAX-RS service */
		return JAXRS_COMPONENT.service(msg);
	}

	@InvocableMethod("GlobalTypeSet")
	public static boolean generateGlobalTypeSet(Message msg) throws CircuitAbortException {
		setMessageBody(msg, generateTypeSet(null, true, true));

		return true;
	}

	@InvocableMethod("DynamicTypeSet")
	public static boolean generateDynamicTypeSet(Message msg) throws CircuitAbortException {
		setMessageBody(msg, generateTypeSet(null, true, false));

		return true;
	}

	@InvocableMethod("StaticTypeSet")
	public static boolean generateStaticTypeSet(Message msg) throws CircuitAbortException {
		setMessageBody(msg, generateTypeSet(null, false, true));

		return true;
	}

	private static void setMessageBody(Message msg, byte[] archive) {
		ContentType contentType = new ContentType(ContentType.Authority.MIME, MEDIATYPE_ZIP);
		HeaderSet headers = new HeaderSet();

		headers.addHeader(HttpHeaders.CONTENT_TYPE, contentType.getType());

		Body body = VordelBodyProvider.readFrom(headers, contentType, archive);

		msg.put(MessageProperties.CONTENT_BODY, body);
	}

	/**
	 * entrypoint for generating typesets from running API Gateway instance.
	 * 
	 * @param info
	 *            JAX-RS URI info which contains query parameters
	 * @return JAX-RS Response with zip file containing typedoc.xml and typeset.xml
	 */
	@GET
	@Produces(MEDIATYPE_ZIP)
	public byte[] generateGlobalTypeSet(@Context UriInfo info) {
		try {
			return generateTypeSet(extractRequestedTypes(info), true, true);
		} catch (CircuitAbortException e) {
			throw new WebApplicationException(e);
		}
	}

	@GET
	@Path("dynamic")
	@Produces(MEDIATYPE_ZIP)
	public byte[] generateDynamicTypeSet(@Context UriInfo info) {
		try {
			return generateTypeSet(extractRequestedTypes(info), true, false);
		} catch (CircuitAbortException e) {
			throw new WebApplicationException(e);
		}
	}

	@GET
	@Path("static")
	@Produces(MEDIATYPE_ZIP)
	public byte[] generateStaticTypeSet(@Context UriInfo info) {
		try {
			return generateTypeSet(extractRequestedTypes(info), false, true);
		} catch (CircuitAbortException e) {
			throw new WebApplicationException(e);
		}
	}

	private static Set<String> extractRequestedTypes(UriInfo info) {
		MultivaluedMap<String, String> query = info.getQueryParameters();
		Set<String> types = new HashSet<String>();

		List<String> requested = query.get("type");

		if (requested != null) {
			for (String type : requested) {
				if (type != null) {
					type = type.trim();

					if (!type.isEmpty()) {
						types.add(type);
					}
				}
			}
		}

		return types;
	}

	public static Element generateDynamicTypeDocs(Element data, Set<String> types) {
		/* recompile dynamic classes */
		List<Class<?>> clazzes = DynamicCompilerModule.compile();

		/* populate entity store data with EntityType elements */
		List<Exception> exceptions = generateTypeDocs(clazzes, types, data);

		for (Exception exception : exceptions) {
			Trace.error(exception.getMessage(), exception.getCause());
		}

		return data;
	}

	public static Element generateStaticTypeDocs(Element data, Set<String> types) {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();

		/* scan thread classpath */
		List<Class<?>> clazzes = ExtensionScanner.scanClasses(loader);

		/* populate entity store data with EntityType elements */
		List<Exception> exceptions = generateTypeDocs(clazzes, types, data);

		for (Exception exception : exceptions) {
			Trace.error(exception.getMessage(), exception.getCause());
		}

		return data;
	}

	public static byte[] generateTypeSet(Set<String> types, boolean dynamicSet, boolean staticSet) throws CircuitAbortException {
		Trace.info("Generating typeset for current instance");

		try {
			Element data = createEntityStoreData();

			if (dynamicSet) {
				generateDynamicTypeDocs(data, types);
			}

			if (staticSet) {
				generateStaticTypeDocs(data, types);
			}

			ByteArrayOutputStream output = new ByteArrayOutputStream();

			ZipOutputStream zos = new ZipOutputStream(output);
			ZipEntry typedoc = new ZipEntry("typedoc.xml");
			ZipEntry typeset = new ZipEntry("typeset.xml");

			writeXml(data.getOwnerDocument(), typedoc, zos);
			writeXml(createTypeSet(typedoc).getOwnerDocument(), typeset, zos);

			zos.close();

			return output.toByteArray();
			// return Response.ok(output.toByteArray()).type(MEDIATYPE_ZIP).build();
		} catch (ParserConfigurationException e) {
			throw new CircuitAbortException("can't create XML Document", e);
		} catch (IOException e) {
			throw new CircuitAbortException("unable to create ZIP Archive", e);
		} catch (TransformerException e) {
			throw new CircuitAbortException("can't write XML Document", e);
		}
	}

	public static Element createEntityStoreData() throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

		factory.setNamespaceAware(true);

		DocumentBuilder builder = factory.newDocumentBuilder();
		Document document = builder.newDocument();
		Element data = document.createElement("entityStoreData");

		document.appendChild(data);

		return data;
	}

	public static Element createTypeSet(ZipEntry... typedocs) throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

		factory.setNamespaceAware(true);

		DocumentBuilder builder = factory.newDocumentBuilder();
		Document document = builder.newDocument();

		Element typesetNode = document.createElement("typeSet");

		for (ZipEntry typedoc : typedocs) {
			Element typedocNode = document.createElement("typedoc");

			typedocNode.setAttribute("file", typedoc.getName());
			typesetNode.appendChild(typedocNode);
		}

		document.appendChild(typesetNode);

		return typesetNode;
	}

	public static List<Exception> generateTypeDocs(List<Class<?>> clazzes, Set<String> types, Element data) {
		List<Exception> exceptions = new ArrayList<Exception>();

		for (Class<?> clazz : clazzes) {
			if (ExtensionScanner.isFilter(clazz)) {
				try {
					Trace.info(String.format("found class '%s' as annotated filter", clazz.getName()));

					Element entityTypeNode = getEntityTypeNode(data, clazz, types);

					if (entityTypeNode != null) {
						Document document = data.getOwnerDocument();

						Map<Method, QuickFilterField> methods = QuickJavaFilter.scanFields(clazz, null);

						for (QuickFilterField field : methods.values()) {
							Element fieldElement = document.createElement("field");
							String[] defaults = field.defaults();

							fieldElement.setAttribute("cardinality", field.cardinality());

							if (defaults.length == 1) {
								fieldElement.setAttribute("default", defaults[0]);
							}

							fieldElement.setAttribute("name", field.name());
							fieldElement.setAttribute("type", field.type());

							if (defaults.length > 1) {
								for (String defaultValue : defaults) {
									Element defaultValueElement = document.createElement("defaultValue");

									defaultValueElement.appendChild(document.createTextNode(defaultValue));
									fieldElement.appendChild(document.createTextNode("\n"));
									fieldElement.appendChild(defaultValueElement);
								}
							}

							entityTypeNode.appendChild(document.createTextNode("\n"));
							entityTypeNode.appendChild(fieldElement);
						}
						Trace.info(String.format("generated typedoc for class '%s'", clazz.getName()));
					} else {
						Trace.info(String.format("'%s' skipped", clazz.getName()));
					}
				} catch (Exception e) {
					exceptions.add(new IllegalStateException(String.format("could not generate entity type for class '%s'", clazz.getName()), e));
				}
			}
		}

		return exceptions;
	}

	private static Element getEntityTypeNode(Element data, Class<?> clazz, Set<String> types) throws ParserConfigurationException, SAXException, IOException {
		QuickFilterType filterType = clazz.getAnnotation(QuickFilterType.class);

		Element entityTypeNode = null;
		String name = filterType.name();

		if ((types == null) || types.isEmpty() || types.contains(name)) {
			Properties props = new Properties();

			InputStream resources = clazz.getResourceAsStream(filterType.resources());
			Document ui = parseXml(clazz.getResourceAsStream(filterType.ui()));

			Document document = data.getOwnerDocument();
			Element versionNode = document.createElement("constant");

			String extend = filterType.extend();
			int version = filterType.version();

			entityTypeNode = document.createElement("entityType");
			entityTypeNode.setAttribute("extends", extend);
			entityTypeNode.setAttribute("name", name);
			versionNode.setAttribute("name", "_version");
			versionNode.setAttribute("type", "integer");
			versionNode.setAttribute("value", Integer.toString(version));

			entityTypeNode.appendChild(document.createTextNode("\n"));
			entityTypeNode.appendChild(versionNode);
			data.appendChild(document.createTextNode("\n"));
			data.appendChild(entityTypeNode);

			try {
				props.load(resources);

				String displayName = props.getProperty("FILTER_DISPLAYNAME", null);
				String description = props.getProperty("FILTER_DESCRIPTION", null);

				if ((name == null) || name.isEmpty()) {
					throw new IllegalArgumentException("filter display Name can't be null or empty");
				}

				if ((description == null) || description.isEmpty()) {
					throw new IllegalArgumentException("filter description can't be null or empty");
				}

				/*
				 * populate filter constants
				 */
				QuickFilterSupport.insertConstant(entityTypeNode, QuickFilterSupport.QUICKFILTER_UI, QuickScriptFilterBuilder.toString(ui));
				QuickFilterSupport.insertConstant(entityTypeNode, QuickFilterSupport.QUICKFILTER_RESOURCES, QuickScriptFilterBuilder.toString(props));
				QuickFilterSupport.insertConstant(entityTypeNode, QuickFilterSupport.QUICKFILTER_GENERATED, QuickFilterSupport.splitValues(props.getProperty("QUICKFILTER_GENERATED", null), true));
				QuickFilterSupport.insertConstant(entityTypeNode, QuickFilterSupport.QUICKFILTER_CONSUMED, QuickFilterSupport.splitValues(props.getProperty("QUICKFILTER_CONSUMED", null), true));
				QuickFilterSupport.insertConstant(entityTypeNode, QuickFilterSupport.QUICKFILTER_REQUIRED, QuickFilterSupport.splitValues(props.getProperty("QUICKFILTER_REQUIRED", null), true));
				QuickFilterSupport.insertConstant(entityTypeNode, QuickFilterSupport.QUICKFILTER_PALETTE, QuickFilterSupport.splitValues(props.getProperty("FILTER_CATEGORY", "Utility"), true));
				QuickFilterSupport.insertConstant(entityTypeNode, QuickFilterSupport.QUICKFILTER_ICON, props.getProperty("FILTER_ICON", "filter_small"));
				QuickFilterSupport.insertConstant(entityTypeNode, QuickFilterSupport.QUICKFILTER_DESCRIPTION, description);
				QuickFilterSupport.insertConstant(entityTypeNode, QuickFilterSupport.QUICKFILTER_DISPLAYNAME, displayName);
				QuickFilterSupport.insertConstant(entityTypeNode, "_version", Integer.toString(version));
				QuickFilterSupport.insertConstant(entityTypeNode, "class", "com.vordel.circuit.ext.filter.quick.QuickJavaFilter");
			} catch (IOException e) {
				throw new IllegalArgumentException("Unable to load filter resources");
			}
		}

		return entityTypeNode;
	}

	private static Document parseXml(InputStream xml) throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

		factory.setNamespaceAware(true);

		DocumentBuilder builder = factory.newDocumentBuilder();

		return builder.parse(xml);
	}

	private static void writeXml(Document document, OutputStream output) throws TransformerException {
		Source source = new DOMSource(document.getDocumentElement());
		Result result = new StreamResult(output);

		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();

		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty(OutputKeys.INDENT, "no");

		transformer.transform(source, result);
	}

	public static void writeXml(Document document, ZipEntry entry, ZipOutputStream zos) throws TransformerException, IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		writeXml(document, output);
		zos.putNextEntry(entry);
		zos.write(output.toByteArray());
		zos.closeEntry();
	}
}
