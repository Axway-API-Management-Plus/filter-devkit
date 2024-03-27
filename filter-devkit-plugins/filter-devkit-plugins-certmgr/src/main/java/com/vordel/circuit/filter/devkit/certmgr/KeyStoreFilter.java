package com.vordel.circuit.filter.devkit.certmgr;

@FunctionalInterface
public interface KeyStoreFilter {
	boolean isExported(KeyStoreEntry entry);
}
