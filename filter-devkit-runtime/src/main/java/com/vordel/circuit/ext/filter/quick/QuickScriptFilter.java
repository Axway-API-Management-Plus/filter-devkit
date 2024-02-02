package com.vordel.circuit.ext.filter.quick;

import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.ext.filter.AbstractQuickFilter;

public class QuickScriptFilter extends AbstractQuickFilter {
	@Override
	public Class<? extends MessageProcessor> getMessageProcessorClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.circuit.ext.filter.quick.QuickScriptFilterProcessor").asSubclass(MessageProcessor.class);
	}
}
