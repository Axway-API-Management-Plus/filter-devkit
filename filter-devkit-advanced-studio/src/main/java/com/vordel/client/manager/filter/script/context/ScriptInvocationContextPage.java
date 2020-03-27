package com.vordel.client.manager.filter.script.context;

import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.eclipse.swt.widgets.Composite;

import com.vordel.client.ext.filter.VordelPageCompat;

public class ScriptInvocationContextPage extends VordelPageCompat {
	public ScriptInvocationContextPage() {
		super("ScriptInvocationContextPage");

		setTitle(resolve("SCRIPTCONTEXT_PAGE_TITLE"));
		setDescription(resolve("SCRIPTCONTEXT_PAGE_DESCRIPTION"));
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
