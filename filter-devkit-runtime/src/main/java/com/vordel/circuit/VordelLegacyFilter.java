package com.vordel.circuit;

public interface VordelLegacyFilter {
    Class<? extends FilterContainerImpl> getConfigPanelClass() throws ClassNotFoundException;
}
