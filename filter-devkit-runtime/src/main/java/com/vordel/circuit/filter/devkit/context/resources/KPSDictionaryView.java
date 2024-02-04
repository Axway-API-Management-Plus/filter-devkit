package com.vordel.circuit.filter.devkit.context.resources;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import com.vordel.common.Dictionary;
import com.vordel.kps.ObjectNotFound;
import com.vordel.kps.Store;
import com.vordel.kps.query.KeyQuery;

public class KPSDictionaryView implements Dictionary {
	private final Store store;
	private final String[] keys;
	private final String[] values;
	private final int index;

	public KPSDictionaryView(KPSResource resource) {
		this(resource.getStore());
	}

	public KPSDictionaryView(Store store) {
		this(store, getKeys(store), null, 0);
	}

	private static String[] getKeys(Store store) {
		return store.getReadKey().toArray(new String[0]);
	}

	private KPSDictionaryView(Store store, String[] keys, String[] values, int index) {
		if (values == null) {
			values = new String[keys.length];
			index = 0;
		}

		this.store = store;
		this.keys = keys;
		this.values = values;
		this.index = index;
	}

	@Override
	public Object get(String key) {
		Object result = null;
		int next = index + 1;

		values[index] = key;

		if (next < values.length) {
			result = new KPSDictionaryView(store, keys, values, next);
		} else {
			List<Pair<String, Object>> pairs = new ArrayList<Pair<String, Object>>();

			for (int index = 0; index < keys.length; index++) {
				pairs.add(Pair.of(keys[index], values[index]));
			}

			KeyQuery query = new KeyQuery(pairs);

			try {
				result = store.getCached(query);
			} catch (ObjectNotFound e) {
				/* ignore */
			}
		}

		return result;
	}

}
