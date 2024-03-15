package com.vordel.client.manager.filter.devkit.studio.script.advanced;

import static com.vordel.client.manager.filter.devkit.studio.script.advanced.AdvancedScriptResources.ADVANCEDSCRIPT_PAGE_DESCRIPTION;
import static com.vordel.client.manager.filter.devkit.studio.script.advanced.AdvancedScriptResources.ADVANCEDSCRIPT_PAGE_TITLE;

import java.io.InputStream;

import org.eclipse.swt.widgets.Composite;

import com.vordel.client.manager.filter.devkit.studio.VordelLegacyPage;

public class AdvancedScriptPage extends VordelLegacyPage {
	public AdvancedScriptPage() {
		super("advancedscriptpage");

		setTitle(ADVANCEDSCRIPT_PAGE_TITLE);
		setDescription(ADVANCEDSCRIPT_PAGE_DESCRIPTION);
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
		return "advancedscriptpage.help";
	}
}
