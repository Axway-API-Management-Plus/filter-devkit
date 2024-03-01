package com.vordel.circuit.filter.devkit.script.advanced;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

import org.codehaus.groovy.runtime.MethodClosure;
import org.python.core.Py;
import org.python.core.PyObject;

import com.vordel.circuit.filter.devkit.context.ExtensionLoader;
import com.vordel.circuit.filter.devkit.script.extension.ScriptExtension;
import com.vordel.circuit.filter.devkit.script.extension.ScriptExtensionBinder;

public abstract class AdvancedScriptRuntimeBinder implements ScriptExtensionBinder {
	private AdvancedScriptRuntimeBinder() {
	}

	private static final Set<String> EXPORTED_CLOSURES = getExportedMethods();
	private static final Set<String> REFLECTIVE_CLOSURES = getReflectiveMethods();

	private static final String getJavascriptClosureTemplate(int argc) {
		StringBuilder body = new StringBuilder();
		StringBuilder args = new StringBuilder();

		for (int index = 0; index < argc; index++) {
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
		exports.add("getExportedResources");

		return Collections.unmodifiableSet(exports);
	}

	private static final Set<String> getReflectiveMethods() {
		Set<String> exports = new HashSet<String>();

		exports.add("reflectResources");
		exports.add("reflectEntryPoints");

		return Collections.unmodifiableSet(exports);
	}

	public static AdvancedScriptRuntimeBinder getScriptBinder(ScriptEngine engine) throws ScriptException {
		/*
		 * singe engine may be registered under multiple names, retrieve language name
		 * from factory
		 */
		ScriptEngineFactory factory = engine.getFactory();
		String language = factory.getLanguageName();

		AdvancedScriptRuntimeBinder binder = null;

		if ("Groovy".equals(language)) {
			binder = AdvancedScriptRuntimeBinder.getGroovyBinder();
		} else if ("ECMAScript".equals(language) || "EmbeddedECMAScript".equals(language)) {
			binder = AdvancedScriptRuntimeBinder.getJavascriptBinder();
		} else if ("python".equals(language)) {
			binder = AdvancedScriptRuntimeBinder.getPythonBinder();
		}

		return binder;
	}

	public abstract void bindRuntime(ScriptEngine engine, AdvancedScriptRuntime runtime) throws ScriptException;

	public static AdvancedScriptRuntimeBinder getJavascriptBinder() {
		return new AdvancedScriptRuntimeBinder() {
			@Override
			public void bindRuntime(ScriptEngine engine, AdvancedScriptRuntime runtime) throws ScriptException {
				Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
				StringBuilder builder = new StringBuilder();

				for (Method method : AdvancedScriptRuntime.class.getDeclaredMethods()) {
					String name = method.getName();

					if (EXPORTED_CLOSURES.contains(name)) {
						String template = getJavascriptClosureTemplate(method.getParameterCount());

						builder.append(String.format(template, name, name));
					}
				}

				/* sets script runtime */
				bindings.put("exportedRuntime", runtime);
				try {
					engine.eval(builder.toString());
				} finally {
					bindings.remove("exportedRuntime");
				}

				ExtensionLoader.bindScriptExtensions(engine, this);
			}

			@Override
			public void bindRuntime(ScriptEngine engine, ScriptExtension runtime, Class<?> clazz) throws ScriptException {
				Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
				StringBuilder builder = new StringBuilder();

				for (Method method : clazz.getDeclaredMethods()) {
					String name = method.getName();
					String template = getJavascriptClosureTemplate(method.getParameterCount());

					builder.append(String.format(template, name, name));
				}

				/* sets script runtime */
				bindings.put("exportedRuntime", runtime);

				try {
					engine.eval(builder.toString());
				} finally {
					bindings.remove("exportedRuntime");
				}
			}
		};
	}

	public static AdvancedScriptRuntimeBinder getPythonBinder() {
		return new AdvancedScriptRuntimeBinder() {
			@Override
			public void bindRuntime(ScriptEngine engine, AdvancedScriptRuntime runtime) throws ScriptException {
				Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
				PyObject pyobj = Py.java2py(runtime);

				for (String method : EXPORTED_CLOSURES) {
					/* sets script runtime */
					bindings.put(method, pyobj.__findattr__(method));
				}

				ExtensionLoader.bindScriptExtensions(engine, this);
			}

			@Override
			public void bindRuntime(ScriptEngine engine, ScriptExtension runtime, Class<?> clazz) throws ScriptException {
				Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
				PyObject pyobj = Py.java2py(runtime);

				for (Method method : clazz.getDeclaredMethods()) {
					String name = method.getName();
					/* sets script runtime */
					bindings.put(name, pyobj.__findattr__(name));
				}
			}
		};
	}

	public static AdvancedScriptRuntimeBinder getGroovyBinder() {
		return new AdvancedScriptRuntimeBinder() {
			@Override
			public void bindRuntime(ScriptEngine engine, AdvancedScriptRuntime runtime) throws ScriptException {
				Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);

				for (Method method : AdvancedScriptRuntime.class.getDeclaredMethods()) {
					String name = method.getName();

					if (EXPORTED_CLOSURES.contains(name)) {
						MethodClosure closure = new MethodClosure(runtime, name);

						bindings.put(name, closure);
					}
				}

				for (Method method : AdvancedScriptRuntime.class.getDeclaredMethods()) {
					String name = method.getName();

					if (REFLECTIVE_CLOSURES.contains(name)) {
						MethodClosure closure = new MethodClosure(runtime, name);

						bindings.put(name, closure);
					}
				}

				ExtensionLoader.bindScriptExtensions(engine, this);
			}

			@Override
			public void bindRuntime(ScriptEngine engine, ScriptExtension runtime, Class<?> clazz) throws ScriptException {
				Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);

				for (Method method : clazz.getDeclaredMethods()) {
					String name = method.getName();
					MethodClosure closure = new MethodClosure(runtime, name);

					bindings.put(name, closure);
				}
			}
		};
	}
}
