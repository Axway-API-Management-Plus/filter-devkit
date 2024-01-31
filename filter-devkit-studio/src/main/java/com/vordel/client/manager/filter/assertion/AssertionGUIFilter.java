package com.vordel.client.manager.filter.assertion;

import java.util.List;
import java.util.Vector;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;

import com.vordel.client.ext.filter.VordelLegacyGUIFilter;
import com.vordel.client.manager.Images;
import com.vordel.client.manager.wizard.VordelPage;

public class AssertionGUIFilter extends VordelLegacyGUIFilter {
	@Override
	public String getSmallIconId() {
		return "has_cert_expired";
	}

	public Image getSmallImage() {
		String id = getSmallIconId();

		return Images.getImageRegistry().get(id);
	}

	public ImageDescriptor getSmallIcon() {
		String id = getSmallIconId();

		return Images.getImageDescriptor(id);
	}

	public String[] getCategories() {
		return new String[] { resolve("FILTER_GROUP_ASSERTION") };
	}

	public String getTypeName() {
		return resolve("ASSERTION_PALETTE_NAME");
	}

	@Override
	public List<VordelPage> getPropertyPages() {
		Vector<VordelPage> pages = new Vector<VordelPage>();

		pages.add(new AssertionPage());
		pages.add(createLogPage());

		return pages;
	}
}
