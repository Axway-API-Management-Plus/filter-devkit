package com.vordel.circuit.ext.filter.quick;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.es.Entity;

public abstract class QuickJavaFilterDefinition {
	public abstract boolean invokeFilter(Circuit c, Message m, MessageProcessor p) throws CircuitAbortException;
	
	public abstract void attachFilter(ConfigContext ctx, Entity entity);
	public abstract void detachFilter();
}
