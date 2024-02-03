package com.vordel.circuit.script.advanced;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.python.core.PyException;
import org.python.core.PyType;
import org.python.core.__builtin__;

import com.axway.gw.es.yaml.model.YamlPK;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;
import com.vordel.es.xes.PortableESPK;
import com.vordel.trace.Trace;

public class AbstractScriptProcessor extends MessageProcessor {
	/**
	 * Attach script global function
	 */
	private static final String ATTACH_FUNCTION_NAME = "attach";
	/**
	 * Detach script global function
	 */
	private static final String DETACH_FUNCTION_NAME = "detach";
	/**
	 * Invoke script global function
	 */
	private static final String INVOKE_FUNCTION_NAME = "invoke";

	protected ScriptEngine engine = null;

	protected String getEngineName(Entity entity) {
		String name = entity.getStringValue("engineName");

		return name;
	}

	protected String getEntityScript(Entity entity) {
		return entity.getStringValue("script");
	}

	/**
	 * Evaluate the script and try to invoke the 'attach' function.
	 * 
	 * @param ctx    current configuration context
	 * @param entity configuration for filter
	 * @throws EntityStoreException if an error occurs while configuring filter
	 */
	@Override
	public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
		super.filterAttached(ctx, entity);

		try {
			/* retrieve and create a script engine */
			engine = getScriptEngine(getEngineName(entity));

			/* execute the script, this will create script functions */
			evaluateScript(ctx, entity, substituteScript(getEntityScript(entity)));

			try {
				attachScript(ctx, entity);
			} catch (NoSuchMethodException ex) {
				throw new IllegalStateException(String.format("can't invoke the script function '%s'", ATTACH_FUNCTION_NAME), ex);
			}
		} catch (ScriptException e) {
			ESPK pk = entity.getPK();
			String entityString = null;

			if (pk == null) {
				entityString = entity.toString();
			} else if (pk instanceof YamlPK) {
				entityString = ((YamlPK) pk).getShortPK().toString();
			} else {
				entityString = PortableESPK.toPortableKey(ctx.getStore(), pk).toShorthandString();
			}

			Trace.error(String.format("There was a problem loading the script '%s': %s", entityString, e.getMessage()));
			Trace.debug(e);
		}
	}

	@Override
	public boolean invoke(Circuit c, Message m) throws CircuitAbortException {
		/* invoke script main function */
		Object result = invokeScript(c, m);

		Trace.debug("Return from script is: " + String.valueOf(result));

		if (!(result instanceof Boolean)) {
			Trace.error(String.format("The script function '%s' must return true or false", INVOKE_FUNCTION_NAME));
			Class<? extends Object> clazz = result == null ? null : result.getClass();

			throw new CircuitAbortException(String.format("Script must return a boolean value. The script returned:  '%s'", String.valueOf(clazz)));
		}

		Trace.debug("ScriptProcessor.invoke: finished with status " + result);

		return ((Boolean) result).booleanValue();
	}

	@Override
	public void filterDetached() {
		try {
			detachScript();
		} catch (NoSuchMethodException e) {
			if (Trace.isDebugEnabled()) {
				Trace.error(String.format("can't invoke the script function '%s'", DETACH_FUNCTION_NAME), e);
			} else {
				Trace.error(String.format("can't invoke the script function '%s'", DETACH_FUNCTION_NAME));
			}
		}
	}

	private static final ScriptEngine getScriptEngine(String engineName) throws ScriptException {
		ScriptEngineManager mgr = new ScriptEngineManager();

		if ((engineName == null) || engineName.isEmpty()) {
			engineName = "nashorn";
		}

		if (engineName.equals("js")) {
			engineName = "rhino";
		}

		ScriptEngine engine = mgr.getEngineByName(engineName);

		if (!(engine instanceof Invocable)) {
			throw new ScriptException(String.format("Unsupported script engine: %s", engineName));
		}

		return engine;
	}

	protected String substituteScript(String script) {
		return script;
	}

	protected void evaluateScript(ConfigContext ctx, Entity entity, String script) throws ScriptException {
		engine.eval(script);
	}

	protected void attachScript(ConfigContext ctx, Entity entity) throws ScriptException, NoSuchMethodException {
		/* try to invoke the attach function */
		invokeFunction(engine, ATTACH_FUNCTION_NAME, ctx, entity);
	}

	protected Object invokeScript(Circuit c, Message m) throws CircuitAbortException {
		/* default behavior for regular script filter */
		return invokeScript(c, m, false, false);
	}

	protected final Object invokeScript(Circuit c, Message m, boolean extended, boolean unwrapExceptions) throws CircuitAbortException {
		try {
			/* invoke script main function */
			Object result = null;

			if (extended) {
				result = invokeFunction(engine, INVOKE_FUNCTION_NAME, c, m);
			} else {
				result = invokeFunction(engine, INVOKE_FUNCTION_NAME, m);
			}

			return result;
		} catch (ScriptException ex) {
			if (unwrapExceptions) {
				CircuitAbortException cause = unwrapException(ex, CircuitAbortException.class);

				if (cause != null) {
					throw cause;
				}
			}

			throw new CircuitAbortException(ex);
		} catch (NoSuchMethodException ex) {
			if (Trace.isDebugEnabled()) {
				Trace.error(String.format("can't invoke the script function '%s'", INVOKE_FUNCTION_NAME), ex);
			} else {
				Trace.error(String.format("can't invoke the script function '%s'", INVOKE_FUNCTION_NAME));
			}

			throw new CircuitAbortException(ex);
		}
	}

	protected void detachScript() throws NoSuchMethodException {
		try {
			/* try to invoke the detach function */
			invokeFunction(engine, DETACH_FUNCTION_NAME);
		} catch (ScriptException ex) {
			if (Trace.isDebugEnabled()) {
				Trace.error("There was a problem unloading the script: " + ex.getMessage(), ex);
			} else {
				Trace.error("There was a problem unloading the script: " + ex.getMessage());
			}
		}
	}

	private static final <T extends Exception> T unwrapException(ScriptException ex, Class<T> kind) {
		T result = null;

		if (ex != null) {
			Throwable cause = ex.getCause();

			if (cause instanceof PyException) {
				/* special case for python, need to unwrap from PyException first */
				PyException pyex = (PyException) cause;

				if (__builtin__.isinstance(pyex.value, PyType.fromClass(kind))) {
					Object t = pyex.value.__tojava__(Throwable.class);

					if ((t != null) && kind.isAssignableFrom(t.getClass())) {
						cause = (Throwable) t;
					}
				}
			}

			/*
			 * unwrap cause if compatible with the given exception kind.
			 */
			if ((cause != null) && kind.isAssignableFrom(cause.getClass())) {
				result = kind.cast(cause);
			}
		}

		return result;
	}

	/**
	 * Simple convenience method to invoke functions in the script.
	 * 
	 * @param name function name
	 * @param args variable arguments array
	 * @return function result (as an object)
	 * @throws NoSuchMethodException if the function does not exists
	 * @throws ScriptException       in case an error is raised within the script
	 */
	private static final Object invokeFunction(ScriptEngine engine, String name, Object... args) throws NoSuchMethodException, ScriptException {
		Invocable invocableEngine = (Invocable) engine;

		return invocableEngine.invokeFunction(name, args);
	}
}
