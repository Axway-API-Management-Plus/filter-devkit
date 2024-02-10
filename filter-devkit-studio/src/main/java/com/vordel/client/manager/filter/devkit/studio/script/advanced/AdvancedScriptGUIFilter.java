package com.vordel.client.manager.filter.devkit.studio.script.advanced;

import static com.vordel.client.manager.filter.devkit.studio.script.advanced.AdvancedScriptResources.ADVANCEDSCRIPT_PALETTE_NAME;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.vordel.client.circuit.model.Circuit;
import com.vordel.client.circuit.model.CircuitStore;
import com.vordel.client.manager.Manager;
import com.vordel.client.manager.ManagerEntityStore;
import com.vordel.client.manager.filter.devkit.studio.VordelLegacyGUIFilter;
import com.vordel.client.manager.wizard.VordelPage;
import com.vordel.common.Dictionary;
import com.vordel.dwe.DelayedESPK;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityType;

public class AdvancedScriptGUIFilter extends VordelLegacyGUIFilter {
	private final SelectionAdapter delegateSelectionAdapter = new DelegateSelectionAdapter();

	private class DelegateSelectionAdapter extends SelectionAdapter {
		@Override
		public void widgetSelected(SelectionEvent e) {
			MenuItem n = (MenuItem) e.getSource();

			if (n.getData() instanceof Circuit) {
				Circuit c = (Circuit) n.getData();

				Manager.getInstance().setSelectedCircuit(c.getEntity().getPK(), getFilter().getEntity().getPK());
			}
		}
	}

	@Override
	public List<Object> getActions() {
		customActions = super.getActions();
		customActions.add(new Separator());
		customActions.add(new JumpToDelegatesAction());

		return customActions;
	}

	@Override
	public String getSmallIconId() {
		return "javascript";
	}

	@Override
	public String[] getCategories() {
		return new String[] { "Utility" };
	}

	@Override
	public String getTypeName() {
		return ADVANCEDSCRIPT_PALETTE_NAME;
	}

	@Override
	public List<VordelPage> getPropertyPages() {
		Vector<VordelPage> pages = new Vector<VordelPage>();

		pages.add(new AdvancedScriptPage());
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

	private Collection<Circuit> getReferencedCircuitPKs() {
		ManagerEntityStore es = Manager.getInstance().getSelectedEntityStore();
		CircuitStore cs = es.getCircuitStore();
		Entity entity = getEntity();

		EntityType resourcesType = es.getTypeForName("ScriptResource");
		Collection<ESPK> resourceList = es.listChildren(entity.getPK(), resourcesType);
		Map<String, Circuit> circuits = new TreeMap<String, Circuit>();

		for (ESPK resourcePK : resourceList) {
			Entity resourceEntity = es.getEntity(resourcePK);
			String resourceType = resourceEntity.getStringValue("resourceType");

			if ("CONFIGURATION_RESOURCE".equals(resourceType)) {
				ESPK reference = resourceEntity.getReferenceValue("configurationReference");

				/* just to be sure that reference is not delayed */
				reference = new DelayedESPK(reference).substitute(Dictionary.empty);

				Entity referenceEntity = es.getEntity(reference);
				String referenceType = referenceEntity.getType().getName();

				if ("FilterCircuit".equals(referenceType)) {
					ESPK delegatedPK = referenceEntity.getPK();
					DelayedESPK delayedPK = delegatedPK instanceof DelayedESPK ? (DelayedESPK) delegatedPK: new DelayedESPK(delegatedPK);
					ESPK circuitPK = delayedPK.substitute(Dictionary.empty);

					if (!EntityStore.ES_NULL_PK.equals(circuitPK)) {
						Circuit circuit = cs.getCircuit(circuitPK);

						circuits.put(circuit.getName(), circuit);
					}
				}
			}
		}

		return circuits.values();
	}

	private class JumpToDelegatesAction extends Action {
		public JumpToDelegatesAction() {
			super(com.vordel.client.manager.filter.Resources.GOTO_DELEGATE, Action.AS_DROP_DOWN_MENU);

			setMenuCreator(new DelegateMenuCreator());
		}

		@Override
		public boolean isEnabled() {
			return getReferencedCircuitPKs().size() > 0;
		}
	}

	public class DelegateMenuCreator implements IMenuCreator {
		private Menu menu;

		@Override
		public void dispose() {
			if (menu != null) {
				menu.dispose();
			}
		}

		@Override
		public Menu getMenu(Control parent) {
			return null;
		}

		@Override
		public Menu getMenu(Menu parent) {
			if (menu != null) {
				menu.dispose();
			}

			menu = new Menu(parent);

			for (Circuit c : getReferencedCircuitPKs()) {
				MenuItem item = new MenuItem(menu, SWT.PUSH);

				item.setText(c.getName());
				item.setData(c);

				item.addSelectionListener(delegateSelectionAdapter);

			}

			return menu;
		}
	}
}
