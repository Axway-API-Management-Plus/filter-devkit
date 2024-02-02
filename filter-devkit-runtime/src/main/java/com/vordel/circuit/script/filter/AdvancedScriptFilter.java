package com.vordel.circuit.script.filter;

import com.vordel.circuit.DefaultFilter;
import com.vordel.circuit.FilterContainerImpl;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.ext.filter.VordelLegacyFilter;

public class AdvancedScriptFilter extends DefaultFilter implements VordelLegacyFilter {
	@Override
	public Class<? extends MessageProcessor> getMessageProcessorClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.circuit.script.advanced.AdvancedScriptProcessor").asSubclass(MessageProcessor.class);
	}

	@Override
	public Class<? extends FilterContainerImpl> getConfigPanelClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.client.manager.filter.script.advanced.AdvancedScriptGUIFilter").asSubclass(FilterContainerImpl.class);
	}
}
