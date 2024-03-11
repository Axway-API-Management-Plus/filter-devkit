package com.vordel.circuit.filter.devkit.context;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.vordel.circuit.filter.devkit.script.ScriptHelper;
import com.vordel.trace.Trace;

/**
 * additional proxy class for script extensions. Its purpose is to display debug
 * messages when calling such extensions.
 * 
 * @author rdesaintleger@axway.com
 */
public class ScriptExtensionHandler implements InvocationHandler {
	private final Object module;

	public ScriptExtensionHandler(Object module) {
		this.module = module;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		try {
			if (Trace.isDebugEnabled()) {
				Trace.debug(String.format("invoke script extension '%s'", method));
			}

			Object result = method.invoke(module, args);

			if (Trace.isDebugEnabled()) {
				if (result instanceof String) {
					Trace.debug(String.format("method '%s' returned \"%s\"", method.getName(), ScriptHelper.encodeLiteral((String) result)));
				} else if ((result instanceof Boolean) || (result instanceof Number)) {
					Trace.debug(String.format("method '%s' returned '%s'", method.getName(), result.toString()));
				} else if (result != null) {
					Trace.debug(String.format("method '%s' returned a value of type '%s'", method.getName(), result.getClass().getName()));
				} else {
					Trace.debug(String.format("method '%s' returned null", method.getName()));
				}
			}

			return result;
		} catch (IllegalAccessException e) {
			/* should not occur */
			throw e;
		} catch (IllegalArgumentException e) {
			/* should not occur */
			throw e;
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();

			if (cause != null) {
				if (Trace.isDebugEnabled()) {
					Trace.debug(String.format("method '%s' thrown exception ", method.getName()), cause);
				}

				throw cause;
			}

			throw e;
		}
	}
}
