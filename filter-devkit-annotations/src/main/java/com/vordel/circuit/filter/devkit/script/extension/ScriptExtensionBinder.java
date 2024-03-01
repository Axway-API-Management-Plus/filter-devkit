package com.vordel.circuit.filter.devkit.script.extension;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

public interface ScriptExtensionBinder {
	void bindRuntime(ScriptEngine engine, ScriptExtension runtime, Class<?> clazz) throws ScriptException;
}
