package com.vordel.circuit.filter.devkit.certmgr;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;

public interface KeyStoreExtensionRuntime {
	KeyStoreResource getKeyStoreResource(String name);

	Boolean lockedCertStoreInvoke(Message msg, String name) throws CircuitAbortException;
}
