package com.vordel.client.manager.filter.legacy;

import java.io.InputStream;

import org.apache.commons.io.IOUtils;

import com.vordel.client.manager.wizard.VordelPage;

public abstract class VordelLegacyPage extends VordelPage {
	public VordelLegacyPage(String pageName) {
		super(pageName);
	}

	@Override
	@SuppressWarnings("deprecation")
	public String resolve(String name, Object... p1) {
		return super.resolve(name, p1);
	}

	@Override
	@SuppressWarnings("deprecation")
	public String resolve(String name) {
		return super.resolve(name);
	}

	@SuppressWarnings("deprecation")
	public static void closeQuietly(InputStream input) {
		IOUtils.closeQuietly(input);
	}
}
