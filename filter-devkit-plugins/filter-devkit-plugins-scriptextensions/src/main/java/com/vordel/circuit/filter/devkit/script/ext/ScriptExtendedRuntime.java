package com.vordel.circuit.filter.devkit.script.ext;

import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.script.extension.ScriptExtension;

public interface ScriptExtendedRuntime extends ScriptExtension {
	String getHTTPVerb(Message msg);
	void setHTTPVerb(Message msg, String verb);
	
	int getHTTPStatus(Message msg);
	void setHTTPStatus(Message msg, int status);
}
