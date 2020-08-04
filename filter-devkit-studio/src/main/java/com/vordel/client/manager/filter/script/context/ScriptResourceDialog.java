package com.vordel.client.manager.filter.script.context;

import com.vordel.client.manager.EntityContextAdapterDialog;
import com.vordel.client.manager.Manager;
import com.vordel.es.Entity;
import com.vordel.es.EntityType;
import com.vordel.es.KeyHolder;
import org.eclipse.swt.widgets.Shell;

public class ScriptResourceDialog extends EntityContextAdapterDialog {
	private String flavor = null;

	public ScriptResourceDialog(Shell parentShell, Manager manager, Entity selected) {
		super(parentShell, "EDIT_SCRIPTCONTEXT_RESOURCE", manager, selected);
	}

	public ScriptResourceDialog(Shell parentShell, Manager manager, EntityType type, KeyHolder parentKeyHolder) {
		super(parentShell, "ADD_SCRIPTCONTEXT_RESOURCE", manager, type, parentKeyHolder);
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
