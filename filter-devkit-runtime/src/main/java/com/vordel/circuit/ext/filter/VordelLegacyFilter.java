package com.vordel.circuit.ext.filter;

import com.vordel.circuit.FilterContainerImpl;

public interface VordelLegacyFilter {
    Class<? extends FilterContainerImpl> getConfigPanelClass() throws ClassNotFoundException;
}
