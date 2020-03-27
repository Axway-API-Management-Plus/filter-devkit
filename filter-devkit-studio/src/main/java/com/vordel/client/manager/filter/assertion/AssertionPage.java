package com.vordel.client.manager.filter.assertion;

import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.eclipse.swt.widgets.Composite;

import com.vordel.client.ext.filter.VordelPageCompat;

public class AssertionPage extends VordelPageCompat {
	public AssertionPage() {
		super("AssertionPage");

		setTitle(resolve("ASSERTION_PAGE_TITLE"));
		setDescription(resolve("ASSERTION_PAGE_DESCRIPTION"));
		setPageComplete(true);
	}

	@Override
	public void createControl(Composite parent) {
		InputStream is = getClass().getResourceAsStream("assertionpage.xml");

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
