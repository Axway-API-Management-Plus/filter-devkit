package com.vordel.circuit.filter.devkit.certmgr.export;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vordel.circuit.filter.devkit.certmgr.KeyStoreEntry;
import com.vordel.circuit.filter.devkit.certmgr.KeyStorePathBuilder;

@FunctionalInterface
public interface KeyStoreExportTransform {
	ObjectNode transform(KeyStorePathBuilder trust, KeyStoreEntry entry, ObjectNode jwk);

	public static class StripClaim implements KeyStoreExportTransform {
		private final String claim;

		public StripClaim(String claim) {
			this.claim = claim;
		}

		@Override
		public ObjectNode transform(KeyStorePathBuilder trust, KeyStoreEntry entry, ObjectNode jwk) {
			jwk.remove(claim);

			return jwk;
		}
	}
}
