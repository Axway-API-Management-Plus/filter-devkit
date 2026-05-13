package com.vordel.client.manager.filter.devkit.studio.script.advanced;

import org.eclipse.osgi.util.NLS;

public class AdvancedScriptResources extends NLS {
	private static final String BUNDLE_NAME = "com.vordel.client.manager.filter.devkit.studio.script.advanced.resources"; //$NON-NLS-1$

	public static String ADVANCEDSCRIPT_PAGE_TITLE;
	public static String ADVANCEDSCRIPT_PAGE_DESCRIPTION;
	public static String ADVANCEDSCRIPT_PALETTE_NAME;

	public static String ADVANCEDSCRIPT;
	public static String ADVANCEDSCRIPT_LANGUAGE_NASHORN;
	public static String ADVANCEDSCRIPT_LANGUAGE_RHINO;
	public static String ADVANCEDSCRIPT_LANGUAGE_GROOVY;
	public static String ADVANCEDSCRIPT_LANGUAGE_PYTHON;

	public static String ADVANCEDSCRIPT_RESOURCES;

	public static String ADVANCEDSCRIPT_ATTRIBUTES;
	public static String ADVANCEDSCRIPT_ATTRIBUTES_REQUIRED;
	public static String ADVANCEDSCRIPT_ATTRIBUTES_GENERATED;
	public static String ADVANCEDSCRIPT_ATTRIBUTES_CONSUMED;

	public static String SCRIPT_RESOURCE_NAME;
	public static String ADD_SCRIPT_RESOURCE;
	public static String EDIT_SCRIPT_RESOURCE;
	public static String DELETE_SCRIPT_RESOURCE;

	public static String SCRIPT_RESOURCE_NAME_LABEL;

	public static String RESOURCE_REFERENCE_CHOICE;
	public static String RESOURCE_REFERENCELABEL;
	public static String RESOURCE_REFERENCETITLE;

	public static String SELECTOR_RESOURCE_CHOICE;
	public static String SELECTOR_EXPRESSION;
	public static String SELECTOR_CLAZZ;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, AdvancedScriptResources.class);
	}

	private AdvancedScriptResources() {
		super();
	}
}
