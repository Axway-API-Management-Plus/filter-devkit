package com.vordel.client.manager.filter.script.advanced;

import com.vordel.client.manager.EntityContextAdapterDialog;
import com.vordel.client.manager.Manager;
import com.vordel.es.Entity;
import com.vordel.es.EntityType;
import com.vordel.es.KeyHolder;
import org.eclipse.swt.widgets.Shell;

public class AdvancedScriptResourceDialog extends EntityContextAdapterDialog {
	private String flavor = null;

	public AdvancedScriptResourceDialog(Shell parentShell, Manager manager, Entity selected) {
		super(parentShell, "Edit Script Resource", manager, selected);
	}

	public AdvancedScriptResourceDialog(Shell parentShell, Manager manager, EntityType type, KeyHolder parentKeyHolder) {
		super(parentShell, "Add Script Resource", manager, type, parentKeyHolder);
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
		// TODO Auto-generated method stub
		return "";
	}
}
