package com.vordel.client.manager.filter.devkit.studio.assertion;

import org.eclipse.osgi.util.NLS;

public class AssertionResources extends NLS {
	private static final String BUNDLE_NAME = "com.vordel.client.manager.filter.devkit.studio.assertion.resources"; //$NON-NLS-1$

	public static String ASSERTION_PAGE_TITLE;
	public static String ASSERTION_PAGE_DESCRIPTION;

	public static String ASSERTION_PALETTE_NAME;

	public static String ADD_ASSERTION_PROPERTY;
	public static String EDIT_ASSERTION_PROPERTY;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, AssertionResources.class);
	}

	private AssertionResources() {
		super();
	}
}
