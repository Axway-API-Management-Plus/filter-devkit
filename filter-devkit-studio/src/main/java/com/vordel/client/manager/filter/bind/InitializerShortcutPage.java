package com.vordel.client.manager.filter.bind;

import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.eclipse.swt.widgets.Composite;

import com.vordel.client.manager.wizard.VordelPage;

public class InitializerShortcutPage extends VordelPage {
	public InitializerShortcutPage() {
		super("InitializerShortcutPage");

		setTitle(_("INITIALIZERSHORTCUT_PAGE_TITLE"));
		setDescription(_("INITIALIZERSHORTCUT_PAGE_DESCRIPTION"));
		setPageComplete(true);
	}

	@Override
	public void createControl(Composite parent) {
		InputStream is = getClass().getResourceAsStream("boundcontextpage.xml");

		try {
			Composite container = render(parent, is);

			setControl(container);
		} finally {
			IOUtils.closeQuietly(is);
		}
	}

	@Override
	public String getHelpID() {
		// TODO Auto-generated method stub
		return "";
	}
}
