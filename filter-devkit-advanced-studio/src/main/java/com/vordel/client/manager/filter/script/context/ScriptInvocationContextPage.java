package com.vordel.client.manager.filter.script.context;

import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.eclipse.swt.widgets.Composite;

import com.vordel.client.manager.wizard.VordelPage;

public class ScriptInvocationContextPage extends VordelPage {
	public ScriptInvocationContextPage() {
		super("ScriptInvocationContextPage");

		setTitle(_("SCRIPTCONTEXT_PAGE_TITLE"));
		setDescription(_("SCRIPTCONTEXT_PAGE_DESCRIPTION"));
		setPageComplete(true);
	}

	@Override
	public void createControl(Composite parent) {
		InputStream is = getClass().getResourceAsStream("scriptcontextpage.xml");

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
