package com.vordel.circuit.script.advanced;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.codehaus.groovy.runtime.MethodClosure;
import org.python.core.Py;
import org.python.core.PyObject;

public abstract class AdvancedScriptRuntimeBinder {
	private AdvancedScriptRuntimeBinder() {
	}

	private static final Set<String> EXPORTED_CLOSURES = getExportedMethods();
	private static final Set<String> REFLECTIVE_CLOSURES = getExtendedMethods();

	private static final String getJavascriptClosureTemplate(int argc) {
		StringBuilder body = new StringBuilder();
		StringBuilder args = new StringBuilder();
		
		for(int index = 0; index < argc; index++) {
			if (index > 0) {
				args.append(", ");
			}
			
			args.append(String.format("arg%d", index));
		}
		
		String argv = args.toString();

		body.append("%s = (function(bindings) {\n");
		body.append("\treturn function(").append(argv).append(") {\n");
		body.append("\t\treturn bindings.%s(").append(argv).append(");\n");
		body.append("\t}\n");
		body.append("}(exportedRuntime));\n");

		return body.toString();
	}

	private static final Set<String> getExportedMethods() {
		Set<String> exports = new HashSet<String>();

		exports.add("setUnwrapCircuitAbortException");
		exports.add("setExtendedInvoke");
		exports.add("getContextResource");
		exports.add("getInvocableResource");
		exports.add("getKPSResource");
		exports.add("getCacheResource");
		exports.add("invokeResource");
		exports.add("substituteResource");

		return Collections.unmodifiableSet(exports);
	}

	private static final Set<String> getExtendedMethods() {
		Set<String> exports = new HashSet<String>();

		exports.add("reflectExtensions");
		exports.add("reflectEntryPoints");
		exports.add("setScriptWebComponent");
		exports.add("getExportedResources");

		return Collections.unmodifiableSet(exports);
	}

	public abstract void bindRuntime(ScriptEngine engine, AdvancedScriptRuntime runtime) throws ScriptException;

	public static AdvancedScriptRuntimeBinder getJavascriptBinder() {
		return new AdvancedScriptRuntimeBinder() {
			@Override
			public void bindRuntime(ScriptEngine engine, AdvancedScriptRuntime runtime) throws ScriptException {
				Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
				StringBuilder builder = new StringBuilder();

				for(Method method : AdvancedScriptRuntime.class.getDeclaredMethods()) {
					String name = method.getName();

					if (EXPORTED_CLOSURES.contains(name)) {
						String template = getJavascriptClosureTemplate(method.getParameterCount());

						builder.append(String.format(template, name, name));
					}
				}

				/* sets script runtime */
				bindings.put("exportedRuntime", runtime);
				engine.eval(builder.toString());
				bindings.remove("exportedRuntime");
			}
		};
	}

	public static AdvancedScriptRuntimeBinder getPythonBinder() {
		return new AdvancedScriptRuntimeBinder() {
			@Override
			public void bindRuntime(ScriptEngine engine, AdvancedScriptRuntime runtime) throws ScriptException {
				Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
				PyObject pyobj = Py.java2py(runtime);

				for(String method : EXPORTED_CLOSURES) {
					/* sets script runtime */
					bindings.put(method, pyobj.__findattr__(method));
				}
			}
		};
	}

	public static AdvancedScriptRuntimeBinder getGroovyBinder() {
		return new AdvancedScriptRuntimeBinder() {
			@Override
			public void bindRuntime(ScriptEngine engine, AdvancedScriptRuntime runtime) throws ScriptException {
				Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);

				for(Method method : AdvancedScriptRuntime.class.getDeclaredMethods()) {
					String name = method.getName();

					if (EXPORTED_CLOSURES.contains(name)) {
						MethodClosure closure = new MethodClosure(runtime, name);

						bindings.put(name, closure);
					}
				}

				for(Method method : AdvancedScriptRuntime.class.getDeclaredMethods()) {
					String name = method.getName();

					if (REFLECTIVE_CLOSURES.contains(name)) {
						MethodClosure closure = new MethodClosure(runtime, name);

						bindings.put(name, closure);
					}
				}
			}
		};
	}
}
