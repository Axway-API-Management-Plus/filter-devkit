package com.vordel.circuit.filter.devkit.certmgr.export;

import java.util.ArrayList;
import java.util.List;

import com.vordel.circuit.filter.devkit.certmgr.KeyStoreFilter;
import com.vordel.circuit.filter.devkit.certmgr.KeyStoreHolder;
import com.vordel.circuit.filter.devkit.certmgr.KeyStorePathBuilder;
import com.vordel.circuit.filter.devkit.certmgr.KeyStoreResource;
import com.vordel.circuit.filter.devkit.context.resources.CacheResource;
import com.vordel.circuit.filter.devkit.context.resources.InvocableResource;

public class KeyStoreExportBuilder {
	private final List<KeyStoreHolder> exported = new ArrayList<KeyStoreHolder>();
	private final List<KeyStoreResource> trusted = new ArrayList<KeyStoreResource>();

	private final List<KeyStoreFilter> filters = new ArrayList<KeyStoreFilter>();
	private final List<KeyStoreExportTransform> transforms = new ArrayList<KeyStoreExportTransform>();

	private CacheResource cache = null;

	private boolean exportPrivate = false;

	/**
	 * adds an keystore to the export service.
	 * 
	 * @param store store to be exported
	 * @return this instanceof builder.
	 */
	public final KeyStoreExportBuilder exportKeyStore(KeyStoreResource store) {
		if (store != null) {
			exported.add(KeyStoreHolder.fromKeyStoreResource(store));
			trusted.add(store);
		}

		return this;
	}

	/**
	 * add trusted certificates for certification path filter.
	 * 
	 * @param store keystore containing trusted certificates (roots and intermediate
	 *              authorities).
	 * @return this instanceof builder.
	 */
	public final KeyStoreExportBuilder addTrustStore(KeyStoreResource store) {
		if (store != null) {
			trusted.add(store);
		}

		return this;
	}

	/**
	 * append a filter to exported entries. if at least a filter returns 'false' the
	 * entry is not exported. filters are only applied on exported entries and not
	 * on certificate chains which is build automatically according to filtered
	 * exported entries. certificate chains are built from given trust stores.
	 * 
	 * @param filter to be applied on keys from exported keystore.
	 * @return this instanceof builder.
	 */
	public final KeyStoreExportBuilder appendFilter(KeyStoreFilter filter) {
		if (filter != null) {
			filters.add(filter);
		}

		return this;
	}

	/**
	 * append a transformation for JWK exports (allows for example to strip kid, x5t
	 * or x5t#256. No control is done on the final returned JSON except for
	 * duplicates (see below). returning null will discard export for this entry.
	 * Entries may also be discarded according to the tree claims kid, x5t, x5t#256.
	 * no duplicates are allowed on exported kid. If no kid is available, no
	 * duplicate on x5t#256 are allowed and if x5t#256 is not available, no
	 * duplicates are allowed on x5t.
	 * 
	 * @param transform transformation to be applied to the serialized JWK.
	 * @return this instanceof builder.
	 */
	public final KeyStoreExportBuilder appendTransform(KeyStoreExportTransform transform) {
		if (transform != null) {
			transforms.add(transform);
		}

		return this;
	}

	/**
	 * Sets a cache resource attached to this export service. this cache will hold
	 * Certificate trust path
	 * 
	 * @param resource cache resource from current script
	 * @return this instanceof builder.
	 */
	public final KeyStoreExportBuilder setCertPathCache(CacheResource resource) {
		this.cache = resource;

		return this;
	}

	/**
	 * choose to export private of public view of entries. (defaults to public view)
	 * 
	 * @param allow if 'true' private view is exported. If false private view is
	 *              used.
	 * @return this instanceof builder.
	 */
	public final KeyStoreExportBuilder exportPrivateJWK(boolean allow) {
		this.exportPrivate = allow;

		return this;
	}

	/**
	 * creates an {@link InvocableResource} which will expose exported certificates
	 * (using JAX-RS). Each time the service is called, it will check for underlying
	 * exported keystores updates. If updated exported entries will be updated
	 * (reload all entries from underlying keystores, apply filters and compute
	 * certpath for each exported certificate).
	 * 
	 * @return certificate export service
	 */
	public InvocableResource build() {
		KeyStorePathBuilder pathBuilder = new KeyStorePathBuilder(cache, trusted.toArray(new KeyStoreResource[0]));

		return new KeyStoreExportService(exported, pathBuilder, filters, transforms, exportPrivate);
	}
}
