package com.vordel.client.ext.filter;

import java.util.MissingResourceException;

import com.vordel.client.manager.GlobalResources;
import com.vordel.client.manager.wizard.VordelPage;
import com.vordel.common.ResourceBase;

public abstract class VordelPageCompat extends VordelPage {
	public VordelPageCompat(String pageName) {
		super(pageName);
	}

	public String resolve(String name) {
		try {
			return getResourceBase().getResourceString(name);
		} catch (MissingResourceException var2) {
			return GlobalResources.get(name);
		}
	}

	public String resolve(String name, Object... objects) {
		try {
			String res = getResourceBase().getResourceString(name);

			return ResourceBase.format(res, objects);
		} catch (MissingResourceException var4) {
			return GlobalResources.get(name, objects);
		}
	}
}
