package com.vordel.client.manager.filter.devkit.studio;

import java.util.List;

import com.vordel.client.manager.filter.DefaultGUIFilter;
import com.vordel.client.manager.filter.log.LogPage;
import com.vordel.client.manager.wizard.VordelPage;

public abstract class VordelLegacyGUIFilter extends DefaultGUIFilter {
	public VordelLegacyGUIFilter() {
		super();
	}

	public abstract String getSmallIconId();
	
	public abstract List<VordelPage> getPropertyPages();

	@Override
	@SuppressWarnings("deprecation")
	public LogPage createLogPage() {
		return super.createLogPage();
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

}
