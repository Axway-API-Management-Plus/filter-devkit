package com.vordel.client.manager.filter.legacy;

import com.vordel.circuit.FilterContainerImpl;

public interface VordelLegacyFilter {
    Class<? extends FilterContainerImpl> getConfigPanelClass() throws ClassNotFoundException;
}
