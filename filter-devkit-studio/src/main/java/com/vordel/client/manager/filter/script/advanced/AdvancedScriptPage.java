package com.vordel.client.manager.filter.script.advanced;

import java.io.InputStream;

import org.eclipse.swt.widgets.Composite;

import com.vordel.client.ext.filter.VordelLegacyPage;

public class AdvancedScriptPage extends VordelLegacyPage {
	public AdvancedScriptPage() {
		super("AdvancedScriptPage");

		setTitle(resolve("ADVANCEDSCRIPT_PAGE_TITLE"));
		setDescription(resolve("ADVANCEDSCRIPT_PAGE_DESCRIPTION"));
		setPageComplete(true);
	}

	@Override
	public void createControl(Composite parent) {
		InputStream is = getClass().getResourceAsStream("advancedscriptpage.xml");

		try {
			Composite container = render(parent, is);

			setControl(container);
		} finally {
			closeQuietly(is);
		}
	}

	@Override
	public String getHelpID() {
		return "";
	}
}
