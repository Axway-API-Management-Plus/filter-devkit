package com.vordel.circuit.filter.devkit.script.advanced;

import java.lang.reflect.Method;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

import org.codehaus.groovy.runtime.MethodClosure;
import org.python.core.Py;
import org.python.core.PyObject;

import com.vordel.circuit.filter.devkit.script.context.GroovyContextRuntime;
import com.vordel.circuit.filter.devkit.script.context.ScriptContextRuntime;
import com.vordel.trace.Trace;

public abstract class AdvancedScriptRuntimeBinder {
	private AdvancedScriptRuntimeBinder() {
	}

	private static final String getJavascriptClosureTemplate(int argc) {
		StringBuilder body = new StringBuilder();

		StringBuilder args = new StringBuilder();

		for (int index = 0; index < argc; index++) {
			if (index > 0) {
				args.append(", ");
			}

			args.append(String.format("arguments[%d]", index));
		}

		String argv = args.toString();

		body.append("%s = (function(bindings) {\n");
		body.append("\treturn function() {\n");
		body.append("\t\treturn bindings.%s(").append(argv).append(");\n");
		body.append("\t}\n");
		body.append("}(exportedRuntime));\n");

		return body.toString();
	}

	public static AdvancedScriptRuntimeBinder getScriptBinder(ScriptEngine engine) {
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

	protected void bind(ScriptEngine engine, AdvancedScriptRuntime runtime) throws ScriptException {
		bind(engine, runtime, AdvancedScriptConfigurator.class.getDeclaredMethods());
		bind(engine, runtime, AdvancedScriptRuntime.class.getDeclaredMethods());
		bind(engine, runtime, ScriptContextRuntime.class.getDeclaredMethods());
	}

	public abstract void bind(ScriptEngine engine, Object instance, Method[] methods) throws ScriptException;

	private static AdvancedScriptRuntimeBinder getJavascriptBinder() {
		return new AdvancedScriptRuntimeBinder() {
			@Override
			public void bind(ScriptEngine engine, Object instance, Method[] methods) throws ScriptException {
				Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
				StringBuilder builder = new StringBuilder();

				for (Method method : methods) {
					String name = method.getName();

					if (method.isVarArgs()) {
						Trace.error(String.format("this script engine does not support variable arguments for functions. script function '%s' skipped", name));
					} else {
						String template = getJavascriptClosureTemplate(method.getParameterCount());

						builder.append(String.format(template, name, name));
					}
				}

				/* sets script runtime */
				bindings.put("exportedRuntime", instance);
				try {
					engine.eval(builder.toString());
				} finally {
					bindings.remove("exportedRuntime");
				}
			}
		};
	}

	private static AdvancedScriptRuntimeBinder getPythonBinder() {
		return new AdvancedScriptRuntimeBinder() {
			@Override
			public void bind(ScriptEngine engine, Object instance, Method[] methods) {
				Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
				PyObject pyobj = Py.java2py(instance);

				for (Method method : methods) {
					String name = method.getName();

					/* sets script runtime */
					bindings.put(name, pyobj.__findattr__(name));
				}
			}
		};
	}

	private static AdvancedScriptRuntimeBinder getGroovyBinder() {
		return new AdvancedScriptRuntimeBinder() {
			@Override
			protected void bind(ScriptEngine engine, AdvancedScriptRuntime runtime) throws ScriptException {
				super.bind(engine, runtime);

				/* add groovy specific bindings */
				bind(engine, runtime, GroovyScriptConfigurator.class.getDeclaredMethods());
				bind(engine, runtime, GroovyContextRuntime.class.getDeclaredMethods());
			}

			@Override
			public void bind(ScriptEngine engine, Object instance, Method[] methods) {
				Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);

				for (Method method : methods) {
					String name = method.getName();
					MethodClosure closure = new MethodClosure(instance, name);

					bindings.put(name, closure);
				}
			}
		};
	}
}
