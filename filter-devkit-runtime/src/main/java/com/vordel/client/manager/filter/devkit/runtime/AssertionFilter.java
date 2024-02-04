package com.vordel.client.manager.filter.devkit.runtime;

import com.vordel.circuit.DefaultFilter;
import com.vordel.circuit.FilterContainerImpl;
import com.vordel.circuit.MessageProcessor;

public class AssertionFilter extends DefaultFilter implements VordelLegacyFilter {
	@Override
	public Class<? extends MessageProcessor> getMessageProcessorClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.circuit.filter.devkit.assertion.AssertionProcessor").asSubclass(MessageProcessor.class);
	}

	@Override
	public Class<? extends FilterContainerImpl> getConfigPanelClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.client.manager.filter.devkit.studio.assertion.AssertionGUIFilter").asSubclass(FilterContainerImpl.class);
	}
}
