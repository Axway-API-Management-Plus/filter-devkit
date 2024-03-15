package com.vordel.client.manager.filter.devkit.studio.script.advanced;

import com.vordel.client.manager.EntityContextAdapterDialog;
import com.vordel.client.manager.Manager;
import com.vordel.es.Entity;
import com.vordel.es.EntityType;
import com.vordel.es.KeyHolder;

import static com.vordel.client.manager.filter.devkit.studio.script.advanced.AdvancedScriptResources.ADD_SCRIPT_RESOURCE;
import static com.vordel.client.manager.filter.devkit.studio.script.advanced.AdvancedScriptResources.EDIT_SCRIPT_RESOURCE;

import org.eclipse.swt.widgets.Shell;

public class AdvancedScriptResourceDialog extends EntityContextAdapterDialog {
	private String flavor = null;

	public AdvancedScriptResourceDialog(Shell parentShell, Manager manager, Entity selected) {
		super(parentShell, EDIT_SCRIPT_RESOURCE, manager, selected);
	}

	public AdvancedScriptResourceDialog(Shell parentShell, Manager manager, EntityType type, KeyHolder parentKeyHolder) {
		super(parentShell, ADD_SCRIPT_RESOURCE, manager, type, parentKeyHolder);
	}

	@Override
	protected String getFlavor() {
		return this.flavor;
	}

	public void setFlavor(String flavor) {
		this.flavor = flavor;
	}

	@Override
	public String getHelpID() {
		return "advancedscriptresources.help";
	}
}
