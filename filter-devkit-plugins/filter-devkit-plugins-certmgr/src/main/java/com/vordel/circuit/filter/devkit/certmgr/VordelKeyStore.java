package com.vordel.circuit.filter.devkit.certmgr;

import java.util.Iterator;
import java.util.Map;

import com.vordel.security.cert.PersonalInfo;
import com.vordel.store.cert.CertStore;

public final class VordelKeyStore extends KeyStoreMapper {
	public VordelKeyStore() {
		reload();
	}

	public void reload() {
		CertStore store = CertStore.getInstance();
		Map<String, PersonalInfo> aliases = store.getAliasToInfo();
		Iterator<Entry<String, PersonalInfo>> iterator = aliases.entrySet().iterator();

		reload(iterator, (entry) -> {
			String alias = entry.getKey();
			PersonalInfo info = entry.getValue();

			return KeyStoreEntry.fromPersonalInfo(alias, info);
		});
	}
}
