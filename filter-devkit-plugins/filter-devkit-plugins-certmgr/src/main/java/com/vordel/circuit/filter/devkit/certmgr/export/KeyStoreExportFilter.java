package com.vordel.circuit.filter.devkit.certmgr.export;

import com.vordel.circuit.filter.devkit.certmgr.KeyStoreEntry;

@FunctionalInterface
public interface KeyStoreExportFilter {
	boolean isExported(KeyStoreEntry entry);
}
