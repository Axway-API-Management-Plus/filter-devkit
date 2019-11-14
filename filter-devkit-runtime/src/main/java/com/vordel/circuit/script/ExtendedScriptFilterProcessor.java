package com.vordel.circuit.script;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.python.core.PyException;
import org.python.core.PyType;
import org.python.core.__builtin__;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;
import com.vordel.trace.Trace;

public class ExtendedScriptFilterProcessor extends MessageProcessor {
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

	private ScriptEngine engine = null;

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
			ScriptEngineManager mgr = new ScriptEngineManager();

			/* retrieve and create a script engine */
			engine = mgr.getEngineByName(getEngineName(entity));

			/* execute the script, this will create script functions */
			engine.eval(getEntityScript(entity));

			try {
				/* try to invoke the attach function */
				invokeFunction(engine, ATTACH_FUNCTION_NAME, ctx, entity);
			} catch (NoSuchMethodException ex) {
				throw new EntityStoreException(String.format("can't invoke the script function '%s'", ATTACH_FUNCTION_NAME), ex);
			}
		} catch (ScriptException ex) {
			throw asEntityStoreException(ex);
		}
	}

	public String getEngineName(Entity entity) {
		return entity.getStringValue("engineName");
	}

	public String getEntityScript(Entity entity) {
		return entity.getStringValue("script");
	}

	@Override
	public void filterDetached() {
		try {
			/* try to invoke the detach function */
			invokeFunction(engine, DETACH_FUNCTION_NAME);
		} catch (ScriptException ex) {
			Trace.error("There was a problem unloading the script: " + ex.getMessage());
			Trace.debug(ex);
		} catch (NoSuchMethodException ex) {
			Trace.error(String.format("can't invoke the script function '%s'", DETACH_FUNCTION_NAME), ex);
		}
	}

	@Override
	public boolean invoke(Circuit c, Message m) throws CircuitAbortException {
		try {
			/* invoke script main function */
			Object result = invokeFunction(engine, INVOKE_FUNCTION_NAME, c, m);

			Trace.debug("Return from script is: " + String.valueOf(result));

			if (!(result instanceof Boolean)) {
				Trace.error(String.format("The script function '%s' must return true or false", INVOKE_FUNCTION_NAME));
				Class<? extends Object> clazz = result == null ? null : result.getClass();

				throw new CircuitAbortException(String.format("Script must return a boolean value. The script returned:  '%s'", String.valueOf(clazz)));
			}

			Trace.debug("ScriptProcessor.invoke: finished with status " + result);

			return ((Boolean) result).booleanValue();
		} catch (ScriptException ex) {
			throw asCircuitAbortException(ex);
		} catch (NoSuchMethodException ex) {
			Trace.error(String.format("can't invoke the script function '%s'", INVOKE_FUNCTION_NAME), ex);

			throw new CircuitAbortException(ex);
		}
	}

	private static EntityStoreException asEntityStoreException(ScriptException ex) {
		EntityStoreException result = null;

		if (ex != null) {
			Throwable cause = ex.getCause();

			if (cause instanceof PyException) {
				/* special case for python, need to unwrap from PyException first */
				PyException pyex = (PyException) cause;

				if (__builtin__.isinstance(pyex.value, PyType.fromClass(EntityStoreException.class))) {
					Object t = pyex.value.__tojava__(Throwable.class);

					if (t instanceof EntityStoreException) {
						cause = (Throwable) t;
					}
				}
			}

			/*
			 * Is the underlying script launched a EntityStoreException, just unwrap it.
			 * Error location is lost, but it allows to use exceptions from script.
			 */
			if (cause instanceof EntityStoreException) {
				result = (EntityStoreException) cause;
			} else {
				result = new EntityStoreException("There was a problem loading the script", ex);
			}
		}

		return result;
	}

	private static CircuitAbortException asCircuitAbortException(ScriptException ex) {
		CircuitAbortException result = null;

		if (ex != null) {
			Throwable cause = ex.getCause();

			if (cause instanceof PyException) {
				/* special case for python, need to unwrap from PyException first */
				PyException pyex = (PyException) cause;

				if (__builtin__.isinstance(pyex.value, PyType.fromClass(CircuitAbortException.class))) {
					Object t = pyex.value.__tojava__(Throwable.class);

					if (t instanceof CircuitAbortException) {
						cause = (Throwable) t;
					}
				}
			}

			/*
			 * Is the underlying script launched a CircuitAbortException, just unwrap it.
			 * Error location is lost, but it allows to use exceptions from script.
			 */
			if (cause instanceof CircuitAbortException) {
				result = (CircuitAbortException) cause;
			} else {
				result = new CircuitAbortException(ex);

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
	private static Object invokeFunction(ScriptEngine engine, String name, Object... args) throws NoSuchMethodException, ScriptException {
		Invocable invocableEngine = (Invocable) engine;

		return invocableEngine.invokeFunction(name, args);
	}
}
