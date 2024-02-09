package com.vordel.client.manager.filter.devkit.studio.script.advanced;

import org.eclipse.osgi.util.NLS;

public class AdvancedScriptResources extends NLS {
	private static final String BUNDLE_NAME = "com.vordel.client.manager.filter.devkit.studio.script.advanced.resources"; //$NON-NLS-1$

	public static String ADVANCEDSCRIPT_PAGE_TITLE;
	public static String ADVANCEDSCRIPT_PAGE_DESCRIPTION;

	public static String FILTER_GROUP_ADVANCEDSCRIPT;
	public static String ADVANCEDSCRIPT_PALETTE_NAME;
	public static String ADVANCEDSCRIPT_PALETTE_ICON;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, AdvancedScriptResources.class);
	}

	private AdvancedScriptResources() {
		super();
	}
}
