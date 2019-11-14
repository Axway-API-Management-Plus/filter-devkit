package com.vordel.circuit.assertion;

import com.vordel.circuit.DefaultFilter;
import com.vordel.circuit.FilterContainerImpl;
import com.vordel.circuit.MessageProcessor;

public class AssertionFilter extends DefaultFilter {
	@Override
	public Class<? extends MessageProcessor> getMessageProcessorClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.circuit.assertion.AssertionProcessor").asSubclass(MessageProcessor.class);
	}

	@Override
	public Class<? extends FilterContainerImpl> getConfigPanelClass() throws ClassNotFoundException {
		return Class.forName("com.vordel.client.manager.filter.assertion.AssertionGUIFilter").asSubclass(FilterContainerImpl.class);
	}
}
