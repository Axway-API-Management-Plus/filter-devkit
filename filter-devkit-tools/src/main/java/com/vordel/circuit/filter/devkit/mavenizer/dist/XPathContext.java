package com.vordel.circuit.filter.devkit.mavenizer.dist;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;

public class XPathContext implements NamespaceContext {
	private final Map<String, String> namespaces;
	private final String defaultURI;

	public XPathContext() {
		this(XMLConstants.NULL_NS_URI, new HashMap<String, String>());
	}

	public XPathContext(String defaultURI, Map<String, String> namespaces) {
		this.defaultURI = defaultURI;
		this.namespaces = namespaces;
	}
	
	public XPathContext addNamespace(String prefix, String uri) {
		namespaces.put(prefix, uri);
		
		return this;
	}

	@Override
	public Iterator<String> getPrefixes(String namespaceURI) {
		throw new IllegalStateException("Not Implemented.");
	}

	@Override
	public String getPrefix(String uri) {
		if (uri == null) {
			throw new NullPointerException("No URI provided");
		} else if (defaultURI.equals(uri)) {
			return XMLConstants.DEFAULT_NS_PREFIX;
		} else if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(uri)) {
			return XMLConstants.XMLNS_ATTRIBUTE;
		} else if (XMLConstants.XML_NS_PREFIX.equals(uri)) {
			return XMLConstants.XML_NS_URI;
		} else {
			for (Entry<String, String> entry : namespaces.entrySet()) {
				if (entry.getValue().equals(uri)) {
					return entry.getKey();
				}
			}
		}

		return null;
	}

	@Override
	public String getNamespaceURI(String prefix) {
		if (prefix == null) {
			throw new IllegalArgumentException();
		} else if (prefix.equals(XMLConstants.XMLNS_ATTRIBUTE)) {
			return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
		} else if (prefix.equals(XMLConstants.XML_NS_PREFIX)) {
			return XMLConstants.XML_NS_URI;
		} else if (prefix.equals(XMLConstants.DEFAULT_NS_PREFIX)) {
			return defaultURI;
		} else {
			String result = namespaces.get(prefix);

			if (result == null) {
				result = XMLConstants.NULL_NS_URI;
			}
			
			return result;
		}
	}
}
