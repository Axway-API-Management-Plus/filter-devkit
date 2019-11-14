package com.vordel.client.manager.filter.bind;

import org.eclipse.swt.widgets.Shell;

import com.vordel.client.manager.EntityContextAdapterDialog;
import com.vordel.client.manager.Manager;
import com.vordel.es.Entity;
import com.vordel.es.EntityType;
import com.vordel.es.KeyHolder;

public class ScriptContextCallPropertyDialog extends EntityContextAdapterDialog {
	private String flavor = null;

	public ScriptContextCallPropertyDialog(Shell parentShell, Manager manager, Entity selected) {
		super(parentShell, "EDIT_PARAMETER", manager, selected);
	}

	public ScriptContextCallPropertyDialog(Shell parentShell, Manager manager, EntityType type, KeyHolder parentKeyHolder) {
		super(parentShell, "ADD_PARAMETER", manager, type, parentKeyHolder);
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
