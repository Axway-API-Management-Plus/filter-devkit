package com.vordel.client.manager.filter.devkit.runtime;

import com.vordel.circuit.MessageProcessor;

public class QuickScriptFilter extends AbstractQuickFilter {
	@Override
	public Class<? extends MessageProcessor> getMessageProcessorClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.circuit.filter.devkit.quick.QuickScriptFilterProcessor").asSubclass(MessageProcessor.class);
	}
}
