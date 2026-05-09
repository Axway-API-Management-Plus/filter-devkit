package com.vordel.el;

import java.beans.FeatureDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Iterator;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;
import javax.el.PropertyNotFoundException;
import javax.el.PropertyNotWritableException;

import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.filter.devkit.context.resources.ContextResource;
import com.vordel.circuit.filter.devkit.context.resources.ContextResourceProvider;
import com.vordel.circuit.filter.devkit.context.resources.FunctionResource;
import com.vordel.circuit.filter.devkit.context.resources.InvocableResource;
import com.vordel.circuit.filter.devkit.context.resources.ResolvedResource;
import com.vordel.circuit.filter.devkit.context.resources.SubstitutableResource;
import com.vordel.circuit.filter.devkit.context.resources.ViewableResource;
import com.vordel.circuit.filter.devkit.script.context.ScriptContextRuntime;
import com.vordel.common.Dictionary;

import de.odysseus.el.misc.TypeConverter;

public class ContextResourceResolver extends ELResolver {
	private static final ContextResourceResolver INSTANCE = new ContextResourceResolver();

	private ContextResourceResolver() {
	}

	public static ContextResourceResolver getInstance() {
		return INSTANCE;
	}

	private static final boolean isResolvable(Object base) {
		return base instanceof ContextResourceProvider;
	}

	private static final Dictionary getDictionary(ELContext context) {
		SelectorContext selector = context instanceof SelectorContext ? (SelectorContext) context : null;

		/* XXX no better way of retrieving context dictionary */
		return selector == null ? null : selector.dict;
	}

	private static String getPropertyName(Object property) {
		String name = property instanceof String ? (String) property : null;

		if ((name == null) || name.isEmpty()) {
			throw new PropertyNotFoundException("property or method name must be a non empty string");
		}

		return name;
	}

	@Override
	public Object getValue(ELContext context, Object base, Object property) {
		Object result = null;

		if (base instanceof ScriptContextRuntime) {
			base = ((ScriptContextRuntime) base).getExportedResources();
		}
		
		if (isResolvable(base)) {
			String name = getPropertyName(property);

			result = getValue(context, (ContextResourceProvider) base, name);
			context.setPropertyResolved(true);
		}

		return result;
	}

	private Object getValue(ELContext context, ContextResourceProvider extension, String property) {
		Dictionary dict = getDictionary(context);

		/* use a regular dictionary to have regular gateway behavior */
		ContextResourceDictionary resolver = new ContextResourceDictionary(extension, dict);

		return resolver.resolve(property);
	}

	@Override
	public Class<?> getType(ELContext context, Object base, Object property) {
		if (base instanceof ScriptContextRuntime) {
			base = ((ScriptContextRuntime) base).getExportedResources();
		}
		
		if (isResolvable(base)) {
			context.setPropertyResolved(true);
		}

		return Object.class;
	}

	@Override
	public void setValue(ELContext context, Object base, Object property, Object value) {
		if (base instanceof ScriptContextRuntime) {
			base = ((ScriptContextRuntime) base).getExportedResources();
		}
		
		if (isResolvable(base)) {
			throw new PropertyNotWritableException("Extensions are not writeable");
		}
	}

	@Override
	public boolean isReadOnly(ELContext context, Object base, Object property) {
		if (base instanceof ScriptContextRuntime) {
			base = ((ScriptContextRuntime) base).getExportedResources();
		}
		
		if (isResolvable(base)) {
			context.setPropertyResolved(true);
		}

		return true;
	}

	@Override
	public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
		return null;
	}

	@Override
	public Class<?> getCommonPropertyType(ELContext context, Object base) {
		if (base instanceof ScriptContextRuntime) {
			base = ((ScriptContextRuntime) base).getExportedResources();
		}
		
		return isResolvable(base) ? String.class : null;
	}

	@Override
	public Object invoke(ELContext context, Object base, Object method, Class<?>[] paramTypes, Object[] params) {
		Object target = null;
		Object result = null;

		if (base instanceof ScriptContextRuntime) {
			base = ((ScriptContextRuntime) base).getExportedResources();
		}
		
		/*
		 * First step, try to extract FunctionResource object (either using regular
		 * dictionary, or direct lookup in resource provider. This way of resolving
		 * 'FunctionResource' allows an implementation without modifying API Gateway
		 * internal dictionary processing (and reuse of existing objects).
		 */

		if (base instanceof Dictionary) {
			String name = getPropertyName(method);

			target = ((Dictionary) base).get(name);
		} else if (isResolvable(base)) {
			String name = getPropertyName(method);

			target = getValue(context, (ContextResourceProvider) base, name);
		}

		/*
		 * If we did extract a FunctionResource, invoke it.
		 */

		if (target instanceof FunctionResource) {
			Dictionary dict = getDictionary(context);

			context.setPropertyResolved(true);

			try {
				result = ((FunctionResource) target).invoke(dict, params);
			} catch (CircuitAbortException e) {
				throw new ELException("Got Exception during invoke", e);
			}
		}

		return result;
	}

	public static Object[] coerceFunctionArguments(Method method, Object[] params) {
		TypeConverter typeConverter = Selector.getTypeConverter();
		Class<?>[] types = method.getParameterTypes();
		Object[] args = new Object[types.length];

		if (method.isVarArgs()) {
			int varargIndex = types.length - 1;

			if (params.length < varargIndex) {
				throw new ELException("Bad argument count");
			}

			for (int i = 0; i < varargIndex; i++) {
				coerceValue(args, i, typeConverter, params[i], types[i]);
			}

			Class<?> varargType = types[varargIndex].getComponentType();
			int length = params.length - varargIndex;
			Object array = null;

			if (length == 1) {
				Object source = params[varargIndex];
				if (source != null && source.getClass().isArray()) {
					if (types[varargIndex].isInstance(source)) { // use source array as is
						array = source;
					} else { // coerce array elements
						length = Array.getLength(source);
						array = Array.newInstance(varargType, length);
						for (int i = 0; i < length; i++) {
							coerceValue(array, i, typeConverter, Array.get(source, i), varargType);
						}
					}
				} else { // single element array
					array = Array.newInstance(varargType, 1);
					coerceValue(array, 0, typeConverter, source, varargType);
				}
			} else {
				array = Array.newInstance(varargType, length);
				for (int i = 0; i < length; i++) {
					coerceValue(array, i, typeConverter, params[varargIndex + i], varargType);
				}
			}

			args[varargIndex] = array;
		} else {
			if (params.length != args.length) {
				throw new ELException("Bad argument count");
			}

			for (int i = 0; i < args.length; i++) {
				coerceValue(args, i, typeConverter, params[i], types[i]);
			}
		}

		return args;
	}

	private static void coerceValue(Object array, int index, TypeConverter typeConverter, Object value, Class<?> type) {
		if (value != null || type.isPrimitive()) {
			if (coerceNumber(array, index, value, type)) {
				/* number types coerced */
			} else if (value != null) {
				Class<?> clazz = value.getClass();
				Object param = null;

				if (DictionaryResolver.isResolvable(clazz)) {
					/* means null, will end in NullPointerException for primitives */
				} else if (type.isAssignableFrom(clazz)) {
					/* no coercion needed */
					param = value;
				} else {
					param = typeConverter.convert(value, type);
				}

				Array.set(array, index, param);
			} else {
				/* will end in NullPointerException */
				Array.set(array, index, null);
			}
		}
	}

	private static boolean coerceNumber(Object array, int index, Object value, Class<?> type) {
		if (value instanceof Number) {
			Object param = null;

			if (type.equals(byte.class) || type.isAssignableFrom(Byte.class)) {
				param = Byte.valueOf(((Number) value).byteValue());
			} else if (type.equals(short.class) || type.isAssignableFrom(Short.class)) {
				param = Short.valueOf(((Number) value).shortValue());
			} else if (type.equals(int.class) || type.isAssignableFrom(Integer.class)) {
				param = Integer.valueOf(((Number) value).intValue());
			} else if (type.equals(char.class) || type.isAssignableFrom(Character.class)) {
				param = Character.valueOf((char) ((Number) value).intValue());
			} else if (type.equals(float.class) || type.isAssignableFrom(Float.class)) {
				param = Float.valueOf(((Number) value).floatValue());
			} else if (type.equals(long.class) || type.isAssignableFrom(Long.class)) {
				param = Long.valueOf(((Number) value).longValue());
			} else if (type.equals(double.class) || type.isAssignableFrom(Double.class)) {
				param = Double.valueOf(((Number) value).doubleValue());
			} else {
				return false;
			}

			Array.set(array, index, param);

			return true;
		} else if (value instanceof Character){
			Object param = null;

			if (type.equals(byte.class) || type.isAssignableFrom(Byte.class)) {
				param = Byte.valueOf((byte) ((Character) value).charValue());
			} else if (type.equals(short.class) || type.isAssignableFrom(Short.class)) {
				param = Short.valueOf((short) ((Character) value).charValue());
			} else if (type.equals(int.class) || type.isAssignableFrom(Integer.class)) {
				param = Integer.valueOf(((Character) value).charValue());
			} else if (type.equals(char.class) || type.isAssignableFrom(Character.class)) {
				param = value;
			} else if (type.equals(float.class) || type.isAssignableFrom(Float.class)) {
				param = Float.valueOf(((Character) value).charValue());
			} else if (type.equals(long.class) || type.isAssignableFrom(Long.class)) {
				param = Long.valueOf(((Character) value).charValue());
			} else if (type.equals(double.class) || type.isAssignableFrom(Double.class)) {
				param = Double.valueOf(((Character) value).charValue());
			} else {
				return false;
			}

			Array.set(array, index, param);

			return true;
		} else {
			return false;
		}
	}

	public static class ContextResourceDictionary implements Dictionary {
		private final ContextResourceProvider extension;
		private final Dictionary dict;

		private ContextResourceDictionary(ContextResourceProvider extension, Dictionary dict) {
			this.extension = extension;
			this.dict = dict;
		}

		/**
		 * internal resolve. This method will return a dictionary slice if no property
		 * is found
		 * 
		 * @param key
		 * @return
		 */
		private Object resolve(String key) {
			Object result = extension.getContextResource(key);

			if (result == null) {
				result = DictionaryResolver.createSlice(this, key);
			} else {
				result = substitute(result, key);
			}

			return result;
		}

		@Override
		public Object get(String key) {
			Object result = extension.getContextResource(key);

			return result == null ? null : substitute(result, key);
		}

		private Object substitute(Object result, String key) {
			do {
				if (result instanceof ViewableResource) {
					/* got a selector special view, return it */
					return ((ViewableResource) result).getResourceView();
				} else if (result instanceof ResolvedResource) {
					/* resource is already resolved, return it */
					return result;
				} else if ((dict instanceof Message) && (result instanceof InvocableResource)) {
					try {
						/* allow chaining invocations */
						result = ((InvocableResource) result).invoke((Message) dict);
					} catch (CircuitAbortException e) {
						throw new ELException("Got Exception during invoke", e);
					}
				} else if (result instanceof SubstitutableResource) {
					/* allow chaining substitutions */
					result = ((SubstitutableResource<?>) result).substitute(dict);
				} else if (result instanceof ContextResource) {
					throw new PropertyNotFoundException(String.format("property '%s' is not readable", key));
				}
			} while (result instanceof ContextResource);

			return result;
		}
	}
}
