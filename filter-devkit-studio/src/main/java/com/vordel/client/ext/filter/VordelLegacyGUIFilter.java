package com.vordel.client.ext.filter;

import java.util.List;

import com.vordel.client.manager.filter.DefaultGUIFilter;
import com.vordel.client.manager.wizard.VordelPage;

public abstract class VordelLegacyGUIFilter extends DefaultGUIFilter {
	public VordelLegacyGUIFilter() {
		super();
	}

	public abstract String getSmallIconId();
	
	public abstract List<VordelPage> getPropertyPages();
}
