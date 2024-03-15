package com.vordel.client.manager.filter.devkit.studio;

import java.util.List;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;

import com.vordel.client.manager.Images;
import com.vordel.client.manager.filter.DefaultGUIFilter;
import com.vordel.client.manager.filter.log.LogPage;
import com.vordel.client.manager.wizard.VordelPage;

public abstract class VordelLegacyGUIFilter extends DefaultGUIFilter {
	public abstract String getSmallIconId();

	public abstract String[] getCategories();

	public abstract List<VordelPage> getPropertyPages();

	public abstract String getTypeName();

	public Image getImage() {
		return getSmallImage();
	}

	public Image getSmallImage() {
		String id = getSmallIconId();

		return Images.getImageRegistry().get(id);
	}

	public ImageDescriptor getSmallIcon() {
		String id = getSmallIconId();

		return Images.getImageDescriptor(id);
	}

	@Override
	@SuppressWarnings("deprecation")
	public LogPage createLogPage() {
		return super.createLogPage();
	}
}
