package com.vordel.client.manager.filter.devkit.studio.quick;

import java.util.List;
import java.util.Vector;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;

import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterSupport;
import com.vordel.client.manager.Images;
import com.vordel.client.manager.filter.devkit.runtime.AbstractQuickFilter;
import com.vordel.client.manager.filter.devkit.studio.VordelLegacyGUIFilter;
import com.vordel.client.manager.wizard.VordelPage;
import com.vordel.es.Entity;
import com.vordel.es.EntityType;

public class QuickFilterGUI extends VordelLegacyGUIFilter {
	private static final String DEFAULT_NAME = "Quick Filter";

	@Override
	public EntityType getEntityType() {
		EntityType entityType = super.getEntityType();
		
		if (entityType == null) {
			/* sounds weird... try to lookup type from entity */
			Entity entity = getEntity();
			
			entityType = entity.getType();
		}
		
		return entityType;
	}

	@Override
	public String getSmallIconId() {
		EntityType entityType = getEntityType();
		String id = entityType == null ? "filter_small" : AbstractQuickFilter.getConstantStringValue(entityType, QuickFilterSupport.QUICKFILTER_ICON);
		
		return id;
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
		EntityType entityType = getEntityType();

		return entityType == null ? new String[] { "Utility" } : AbstractQuickFilter.getConstantStringValues(entityType, QuickFilterSupport.QUICKFILTER_PALETTE, true);
	}

	public String getTypeName() {
		EntityType entityType = getEntityType();
		
		if (entityType == null) {
			Entity entity = getEntity();
			
			entityType = entity.getType();
		}
		
		String typeName = entityType == null ? DEFAULT_NAME : AbstractQuickFilter.getConstantStringValue(entityType, QuickFilterSupport.QUICKFILTER_DISPLAYNAME);

		return typeName;
	}

	@Override
	public List<VordelPage> getPropertyPages() {
		Vector<VordelPage> pages = new Vector<VordelPage>();

		pages.add(new QuickFilterPage());
		pages.add(createLogPage());

		return pages;
	}
}