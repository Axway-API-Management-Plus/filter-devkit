package com.vordel.client.manager.filter.bind;

import java.util.List;
import java.util.Vector;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;

import com.vordel.circuit.script.bind.InitializerShortcutFilter;
import com.vordel.client.circuit.model.Circuit;
import com.vordel.client.manager.Images;
import com.vordel.client.manager.Manager;
import com.vordel.client.manager.filter.DefaultGUIFilter;
import com.vordel.client.manager.wizard.VordelPage;
import com.vordel.config.ConfigContext;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;
import com.vordel.es.EntityType;
import com.vordel.trace.Trace;

public class InitializerShortcutGUIFilter extends DefaultGUIFilter {
	private Circuit remoteCircuit = null;

	@Override
	public String getSmallIconId() {
		return "web_service";
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
		return new String[] { resolve("FILTER_GROUP_BOUNDCALL") };
	}

	public String getTypeName() {
		return resolve("INITIALIZERSHORTCUT_PALETTE_NAME");
	}

	@Override
	public List<VordelPage> getPropertyPages() {
		Vector<VordelPage> pages = new Vector<VordelPage>();

		pages.add(new InitializerShortcutPage());
		pages.add(createLogPage());

		return pages;
	}

	public final InitializerShortcutFilter getInitializerShortcutFilter() {
		return (InitializerShortcutFilter) getFilter();
	}

	@Override
	public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
		super.filterAttached(ctx, entity);

		updateInitializerCircuit(entity);
	}

	@Override
	public void childAdded(Entity child) {
		super.childAdded(child);

		updateChildEntity(Manager.getInstance().getSelectedEntityStore().getSolutionPack().getStore(), child);
	}

	@Override
	public void childDeleted(ESPK parentPK, ESPK childPK) {
		super.childDeleted(parentPK, childPK);

		EntityStore es = Manager.getInstance().getSelectedEntityStore().getSolutionPack().getStore();
		Entity child = es.getEntity(childPK);

		updateChildEntity(es, child);
	}

	@Override
	public void childUpdated(Entity child) {
		super.childUpdated(child);

		updateChildEntity(Manager.getInstance().getSelectedEntityStore().getSolutionPack().getStore(), child);
	}

	@Override
	public void filterDetached() {
		super.filterDetached();

		this.remoteCircuit = null;
	}

	@Override
	protected void finalize() {
		this.remoteCircuit = null;
	}

	@Override
	public void entityUpdated(Entity entity) {
		super.entityUpdated(entity);

		updateInitializerCircuit(entity);
	}

	@Override
	public List<Object> getActions() {
		customActions = super.getActions();
		customActions.add(new Separator());
		customActions.add(new JumpToInitializerCircuit());

		return this.customActions;
	}

	private void updateInitializerCircuit(Entity entity) {
		InitializerShortcutFilter filter = getInitializerShortcutFilter();
		ESPK initializerPK = filter.getInitializerPK();
		Circuit remoteCircuit = null;

		if (initializerPK != null) {
			ESPK parentPK = getEntity().getParentPK();

			if (parentPK.equals(initializerPK)) {
				Trace.fatal(String.format("Configuration error the bound context '%s' is pointing to it's parent circuit", filter.getName()));

				initializerPK = null;
			} else {
				remoteCircuit = Manager.getInstance().getSelectedEntityStore().getCircuitStore().getCircuit(initializerPK);
			}
		}

		this.remoteCircuit = remoteCircuit;
	}

	private void updateChildEntity(EntityStore es, Entity child) {
		EntityType outputType = es.getTypeForName("OutputSelector");
		EntityType childType = child.getType();

		if (childType.equals(outputType)) {
			entityUpdated(getEntity());
		}
	}

	private class JumpToInitializerCircuit extends Action {
		public JumpToInitializerCircuit() {
			super(resolve("GOTO_INITIALIZER"));
		}

		public void run() {
			if (remoteCircuit != null) {
				Manager mg = Manager.getInstance();

				mg.setSelectedCircuit(remoteCircuit.getEntity().getPK(), getFilter().getEntity().getPK());
			}
		}

		public boolean isEnabled() {
			return remoteCircuit != null;
		}
	}
}
