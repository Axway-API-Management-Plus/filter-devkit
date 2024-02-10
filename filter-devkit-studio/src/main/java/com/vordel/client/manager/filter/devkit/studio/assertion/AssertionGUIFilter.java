package com.vordel.client.manager.filter.devkit.studio.assertion;

import static com.vordel.client.manager.filter.devkit.studio.assertion.AssertionResources.ASSERTION_PALETTE_NAME;

import java.util.List;
import java.util.Vector;

import com.vordel.client.manager.filter.devkit.studio.VordelLegacyGUIFilter;
import com.vordel.client.manager.wizard.VordelPage;

public class AssertionGUIFilter extends VordelLegacyGUIFilter {
	@Override
	public String getSmallIconId() {
		return "has_cert_expired";
	}

	@Override
	public String[] getCategories() {
		return new String[] { "Utility" };
	}

	@Override
	public String getTypeName() {
		return ASSERTION_PALETTE_NAME;
	}

	@Override
	public List<VordelPage> getPropertyPages() {
		Vector<VordelPage> pages = new Vector<VordelPage>();

		pages.add(new AssertionPage());
		pages.add(createLogPage());

		return pages;
	}
}
