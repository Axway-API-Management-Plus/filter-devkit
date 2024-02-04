package com.vordel.circuit.filter.devkit.samples

import com.vordel.circuit.CircuitAbortException
import com.vordel.circuit.InvocationEngine
import com.vordel.circuit.Message
import com.vordel.config.Circuit
import com.vordel.config.ConfigContext
import com.vordel.dwe.DelayedESPK
import com.vordel.es.ESPK
import com.vordel.es.Entity
import com.vordel.es.EntityStore

import groovy.transform.Field

@Field private Circuit circuit;
@Field private Object context;

public void attach(ConfigContext ctx, Entity entity) {
	DelayedESPK circuitPK = new DelayedESPK(entity.getReferenceValue("circuitPK"));
	ESPK substitutedPK = circuitPK.substitute(com.vordel.common.Dictionary.empty);
	
	circuit = null;
	context = null;
	
	if (!EntityStore.ES_NULL_PK.equals(substitutedPK)) {
		circuit = ctx.getCircuit(substitutedPK);
		context = substitutedPK;
	}
}

public void detach() {

}

public boolean invoke(Circuit c, Message m) throws CircuitAbortException {
	boolean result = false;
	
	if (circuit != null) {
		result = InvocationEngine.invokeCircuit(circuit, context, m);
	}
	
	return result;
}