package com.vordel.circuit.filter.devkit.context;

import com.vordel.config.ConfigContext;
import com.vordel.es.EntityStoreException;

/**
 * Marker interface for instantiable modules. If the class implement this
 * interface. It will be instantiated using a no arg contructor,
 * {@link #attachModule(ConfigContext)} method will be called at
 * initialization time before any filter or processor attachment.
 * {@link #detachModule()} method will be called at {@link ExtensionLoader}
 * unload after all filters and processors have been detached.
 * 
 * @author rdesaintleger@axway.com
 */
public interface ExtensionModule {
	/**
	 * attach call for the module.
	 * 
	 * @param ctx    current configuration context
	 * @throws EntityStoreException if any error occurs
	 */
	public void attachModule(ConfigContext ctx) throws EntityStoreException;

	/**
	 * detach call for the module.
	 */
	public void detachModule();
}
