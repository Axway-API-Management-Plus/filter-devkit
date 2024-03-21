package com.vordel.circuit.filter.devkit.context.resources;

import java.util.Map;

import com.vordel.config.ConfigContext;
import com.vordel.es.ESPK;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreException;
import com.vordel.es.EntityType;
import com.vordel.kps.Model;
import com.vordel.kps.Store;
import com.vordel.kps.impl.KPS;
import com.vordel.persistence.kps.KPSInterface;

public class KPSStoreResource extends KPSResource {
	private final Store store;

	public KPSStoreResource(ConfigContext ctx, Entity entity, String reference) throws EntityStoreException {
		this(ctx, entity.getReferenceValue(reference));
	}

	public KPSStoreResource(ConfigContext ctx, ESPK storePK) throws EntityStoreException {
		this(getStoreByIdentity(getStoreIdentity(ctx, storePK)));

		if (store == null) {
			throw new EntityStoreException("Unable to find KPS Store");
		}
	}

	public KPSStoreResource(Store store) {
		this.store = store;
	}

	public KPSStoreResource(String alias) {
		this(getStoreByAlias(alias));
	}

	public static Model getModel() {
		KPSInterface instance = KPS.getInstance();

		return instance.getModel();
	}

	public static Store getStoreByIdentity(String identity) {
		Model model = getModel();
		Map<String, Store> stores = model.getStores();

		return stores.get(identity);
	}

	public static Store getStoreByAlias(String alias) {
		Model model = getModel();
		Map<String, Store> stores = model.getAliases();

		return stores.get(alias);
	}

	public static String getStoreIdentity(String packageName, String storeName) {
		StringBuilder result = new StringBuilder();

		if (packageName != null) {
			result.append(packageName);
		}

		result.append('_');

		if (storeName != null) {
			result.append(storeName);
		}

		return result.toString();
	}

	private static String getStoreIdentity(ConfigContext ctx, ESPK storePK) throws EntityStoreException {
		EntityStore es = ctx.getStore();
		Entity storeEntity = es.getEntity(storePK);
		Entity packageEntity = getParentPackage(es, storeEntity.getParentPK());
	
		String storeName = storeEntity.getStringValue("name");
		String packageName = packageEntity.getStringValue("name");
	
		return getStoreIdentity(packageName, storeName);
	}

	private static Entity getParentPackage(EntityStore es, ESPK parentPK) {
		EntityType packageType = es.getTypeForName("KPSPackage");
		Entity packageEntity = null;

		while ((packageEntity == null) && (parentPK != null)) {
			Entity parentEntity = es.getEntity(parentPK);

			if (parentEntity != null) {
				if (packageType.equals(parentEntity.getType())) {
					packageEntity = parentEntity;
				} else {
					parentPK = parentEntity.getParentPK();

					if ((parentPK != null) && (parentPK.equals(es.getRootPK()))) {
						parentPK = null;
					}
				}
			}
		}

		return packageEntity;
	}

	public Store getStore() {
		return store;
	}
}
