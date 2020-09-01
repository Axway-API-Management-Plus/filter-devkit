package com.vordel.el;

import java.beans.FeatureDescriptor;
import java.util.Iterator;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;
import javax.el.PropertyNotFoundException;

import com.vordel.circuit.script.context.resources.ContextResourceProvider;
import com.vordel.circuit.script.context.resources.SubstitutableResource;
import com.vordel.circuit.script.context.resources.ViewableResource;
import com.vordel.common.Dictionary;

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

		return selector == null ? null : selector.dict;
	}

	@Override
	public Object getValue(ELContext context, Object base, Object property) {
		Object result = null;

		if (isResolvable(base)) {
			ContextResourceProvider extension = (ContextResourceProvider) base;
			Dictionary dict = getDictionary(context);

			String name = property instanceof String ? (String) property : null;

			if (name == null) {
				throw new PropertyNotFoundException("property does not exists");
			}

			/* use a regular dictionary to have regular gateway behavior */
			ContextResourceDictionary resolver = new ContextResourceDictionary(extension, dict);

			context.setPropertyResolved(true);
			result = resolver.resolve(name);
		}

		return result;
	}

	@Override
	public Class<?> getType(ELContext context, Object base, Object property) {
		if (isResolvable(base)) {
			context.setPropertyResolved(true);
		}

		return Object.class;
	}

	@Override
	public void setValue(ELContext context, Object base, Object property, Object value) {
		if (isResolvable(base)) {
			throw new ELException("Extensions are not writeable");
		}
	}

	@Override
	public boolean isReadOnly(ELContext context, Object base, Object property) {
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
		return isResolvable(base) ? String.class : null;
	}

	public static class ContextResourceDictionary implements Dictionary {
		private final ContextResourceProvider extension;
		private final Dictionary dict;

		public ContextResourceDictionary(ContextResourceProvider extension, Dictionary dict) {
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
			Object result = extension.getContextResource(dict, key);

			if (result == null) {
				result = DictionaryResolver.createSlice(this, key);
			} else {
				result = substitute(result, key);
			}

			return result;
		}

		@Override
		public Object get(String key) {
			Object result = extension.getContextResource(dict, key);

			return result == null ? null : substitute(result, key);
		}

		private Object substitute(Object result, String key) {
			if (result instanceof ViewableResource) {
				result = ((ViewableResource) result).getResourceView();
			} else if (result instanceof SubstitutableResource) {
				while (result instanceof SubstitutableResource) {
					result = ((SubstitutableResource<?>) result).substitute(dict);
				}
			} else if (result != null) {
				throw new PropertyNotFoundException(String.format("property '%' is not readable", key));
			}

			return result;
		}
	}
}
