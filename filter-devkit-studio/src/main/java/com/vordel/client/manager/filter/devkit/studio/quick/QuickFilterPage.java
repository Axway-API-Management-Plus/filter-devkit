package com.vordel.client.manager.filter.devkit.studio.quick;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.eclipse.swt.widgets.Composite;

import com.vordel.circuit.filter.devkit.quick.annotations.QuickFilterSupport;
import com.vordel.client.manager.filter.devkit.runtime.AbstractQuickFilter;
import com.vordel.client.manager.filter.devkit.studio.VordelLegacyPage;
import com.vordel.common.ResourceBase;
import com.vordel.es.Entity;
import com.vordel.es.EntityType;

public class QuickFilterPage extends VordelLegacyPage 
{    
	public QuickFilterPage() {
		super("QuickFilterPage");

		setPageComplete(false);
	}

	public String getHelpID() {
		return "QuickFilterPage.help";
	}

	public boolean performFinish() {
		return true;
	}

	public void createControl(Composite parent) {
		Entity e = getEntity();
		EntityType filterType = e.getType();
		String xmlUI = AbstractQuickFilter.getConstantStringValue(filterType, QuickFilterSupport.QUICKFILTER_UI);
		ResourceBase resourceBase = new QuickFilterResourceBase(filterType, getClass());
		
		setResourceBase(resourceBase);
		
		// set the title and description from the entity
		setTitle(AbstractQuickFilter.getConstantStringValue(filterType, QuickFilterSupport.QUICKFILTER_DISPLAYNAME));
		setDescription(AbstractQuickFilter.getConstantStringValue(filterType, QuickFilterSupport.QUICKFILTER_DESCRIPTION));

		// set the UI controls from the xmlUI
		InputStream in = new ByteArrayInputStream(xmlUI.getBytes());
		Composite panel = render(parent,  in);
		setControl(panel);
		setPageComplete(true);
	} 
}