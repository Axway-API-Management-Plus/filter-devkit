package com.vordel.client.manager.filter.groovy;

import java.io.InputStream;

import org.eclipse.swt.widgets.Composite;

import com.vordel.client.ext.filter.VordelLegacyPage;

public class GroovyScriptPage extends VordelLegacyPage {
	public GroovyScriptPage() {
		super("GroovyScriptPage");

		setTitle(resolve("GROOVYSCRIPT_PAGE_TITLE"));
		setDescription(resolve("GROOVYSCRIPT_PAGE_DESCRIPTION"));
		setPageComplete(true);
	}

	@Override
	public void createControl(Composite parent) {
		InputStream is = getClass().getResourceAsStream("groovyscriptpage.xml");

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
