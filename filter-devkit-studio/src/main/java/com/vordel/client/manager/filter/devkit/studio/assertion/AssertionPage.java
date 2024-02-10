package com.vordel.client.manager.filter.devkit.studio.assertion;

import static com.vordel.client.manager.filter.devkit.studio.assertion.AssertionResources.ASSERTION_PAGE_DESCRIPTION;
import static com.vordel.client.manager.filter.devkit.studio.assertion.AssertionResources.ASSERTION_PAGE_TITLE;

import java.io.InputStream;

import org.eclipse.swt.widgets.Composite;

import com.vordel.client.manager.filter.devkit.studio.VordelLegacyPage;

public class AssertionPage extends VordelLegacyPage {
	public AssertionPage() {
		super("AssertionPage");

		setTitle(ASSERTION_PAGE_TITLE);
		setDescription(ASSERTION_PAGE_DESCRIPTION);
		setPageComplete(true);
	}

	@Override
	public void createControl(Composite parent) {
		InputStream is = getClass().getResourceAsStream("assertionpage.xml");

		try {
			Composite container = render(parent, is);

			setControl(container);
		} finally {
			closeQuietly(is);
		}
	}

	@Override
	public String getHelpID() {
		return "assertionfilter.help";
	}
}
