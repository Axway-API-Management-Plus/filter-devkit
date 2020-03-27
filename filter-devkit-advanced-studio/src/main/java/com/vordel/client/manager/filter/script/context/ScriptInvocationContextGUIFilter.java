package com.vordel.client.manager.filter.script.context;

import java.util.List;
import java.util.Vector;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;

import com.vordel.client.ext.filter.DefaultGUIFilterCompat;
import com.vordel.client.manager.Images;
import com.vordel.client.manager.Manager;
import com.vordel.client.manager.wizard.VordelPage;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityType;

public class ScriptInvocationContextGUIFilter extends DefaultGUIFilterCompat {
	@Override
	public String getSmallIconId() {
		return "copy";
	}

	public Image getSmallImage() {
		String id = getSmallIconId();

		return Images.getImageRegistry().get(id);
	}

	public ImageDescriptor getSmallIcon() {
		String id = getSmallIconId();

		return Images.getImageDescriptor(id);
	}

	public String[] getCategories() {
		return new String[] { resolve("FILTER_GROUP_SCRIPTCONTEXT") };
	}

	public String getTypeName() {
		return resolve("SCRIPTCONTEXT_PALETTE_NAME");
	}

	@Override
	public List<VordelPage> getPropertyPages() {
		Vector<VordelPage> pages = new Vector<VordelPage>();

		pages.add(new ScriptInvocationContextPage());
		pages.add(createLogPage());

		return pages;
	}

	@Override
	public void childAdded(Entity child) {
		super.childAdded(child);

		updateChildResource(Manager.getInstance().getSelectedEntityStore().getSolutionPack().getStore(), child);
	}

	@Override
	public void childDeleted(ESPK parentPK, ESPK childPK) {
		super.childDeleted(parentPK, childPK);

		EntityStore es = Manager.getInstance().getSelectedEntityStore().getSolutionPack().getStore();
		Entity child = es.getEntity(childPK);

		updateChildResource(es, child);
	}

	@Override
	public void childUpdated(Entity child) {
		super.childUpdated(child);

		updateChildResource(Manager.getInstance().getSelectedEntityStore().getSolutionPack().getStore(), child);
	}

	private void updateChildResource(EntityStore es, Entity child) {
		EntityType resourceType = es.getTypeForName("ScriptResource");
		EntityType childType = child.getType();

		if (childType.equals(resourceType)) {
			entityUpdated(getEntity());
		}
	}
}
