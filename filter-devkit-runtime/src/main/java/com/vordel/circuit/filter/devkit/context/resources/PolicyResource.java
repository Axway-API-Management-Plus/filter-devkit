package com.vordel.circuit.filter.devkit.context.resources;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.InvocationEngine;
import com.vordel.circuit.Message;
import com.vordel.common.Dictionary;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.dwe.DelayedESPK;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;

public final class PolicyResource implements InvocableResource {
	private final Circuit circuit;
	private final ESPK circuitPK;

	public PolicyResource(ConfigContext ctx, Entity entity, String reference) throws EntityStoreException {
		this(ctx, entity.getReferenceValue(reference));
	}

	public PolicyResource(ConfigContext ctx, ESPK delegatedPK) throws EntityStoreException {
		DelayedESPK delayedPK = delegatedPK instanceof DelayedESPK ? (DelayedESPK) delegatedPK : new DelayedESPK(delegatedPK);
		ESPK circuitPK = delayedPK.substitute(Dictionary.empty);
		Circuit circuit = null;

		if (EntityStore.ES_NULL_PK.equals(circuitPK)) {
			circuitPK = null;
		} else {
			circuit = ctx.getCircuit(circuitPK);
		}

		this.circuitPK = circuitPK;
		this.circuit = circuit;
	}

	public PolicyResource(Circuit circuit, ESPK circuitPK) {
		this.circuitPK = circuitPK;
		this.circuit = circuit;
	}

	public final Circuit getCircuit() {
		return circuit;
	}

	public final ESPK getCircuitPK() {
		return circuitPK;
	}

	public ESPK getContext() {
		return getCircuitPK();
	}

	@Override
	public boolean invoke(Message message) throws CircuitAbortException {
		Circuit circuit = getCircuit();

		if (circuit == null) {
			throw new CircuitAbortException("No Policy configured for this resource");
		}

		return InvocationEngine.invokeCircuit(circuit, getContext(), message);
	}
}
