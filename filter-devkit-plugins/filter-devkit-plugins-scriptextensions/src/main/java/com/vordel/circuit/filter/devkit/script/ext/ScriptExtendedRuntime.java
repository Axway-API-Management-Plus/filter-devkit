package com.vordel.circuit.filter.devkit.script.ext;

import com.vordel.circuit.Message;

public interface ScriptExtendedRuntime {
	String getHTTPVerb(Message msg);
	void setHTTPVerb(Message msg, String verb);
	
	int getHTTPStatus(Message msg);
	void setHTTPStatus(Message msg, int status);
}
