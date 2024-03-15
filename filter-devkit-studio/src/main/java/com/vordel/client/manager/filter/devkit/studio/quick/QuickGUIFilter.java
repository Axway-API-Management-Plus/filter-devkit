package com.vordel.client.manager.filter.devkit.studio.quick;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
import com.vordel.common.Dictionary;
import com.vordel.dwe.DelayedESPK;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityType;
import com.vordel.es.FieldType;

public abstract class QuickGUIFilter extends VordelLegacyGUIFilter {
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

		if (getCircuitReferenceNames().size() > 0) {
			customActions.add(new Separator());
			customActions.add(new JumpToDelegatesAction());
		}

		return customActions;
	}

	private Set<String> getCircuitReferenceNames() {
		Entity entity = getEntity();

		EntityType resourcesType = entity.getType();
		Set<String> refs = new TreeSet<String>();

		for (String fieldName : resourcesType.getAllFieldNames()) {
			FieldType fieldType = resourcesType.getFieldType(fieldName);

			if (fieldType.isRefType() && "FilterCircuit".equals(fieldType.getRefType())) {
				/* type is a policy register it */
				refs.add(fieldName);
			}
		}

		return refs;
	}

	private Collection<Circuit> getReferencedCircuitPKs() {
		ManagerEntityStore es = Manager.getInstance().getSelectedEntityStore();
		CircuitStore cs = es.getCircuitStore();

		Map<String, Circuit> circuits = new TreeMap<String, Circuit>();
		Set<String> refs = getCircuitReferenceNames();
		Entity entity = getEntity();

		for (String fieldName : refs) {
			ESPK delegatedPK = entity.getReferenceValue(fieldName);
			DelayedESPK delayedPK = delegatedPK instanceof DelayedESPK ? (DelayedESPK) delegatedPK : new DelayedESPK(delegatedPK);
			ESPK circuitPK = delayedPK.substitute(Dictionary.empty);

			if (!EntityStore.ES_NULL_PK.equals(circuitPK)) {
				Circuit circuit = cs.getCircuit(circuitPK);

				circuits.put(circuit.getName(), circuit);
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
