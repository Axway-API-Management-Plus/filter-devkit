package com.vordel.circuit.filter.devkit.certmgr;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class KeyStoreFilterIterator implements Iterator<KeyStoreEntry> {
	private final Iterator<KeyStoreEntry> iterator;
	private final KeyStoreFilter filter;

	private KeyStoreEntry next = null;

	public KeyStoreFilterIterator(Iterable<KeyStoreEntry> iterable, KeyStoreFilter filter) {
		this(iterable.iterator(), filter);
	}

	public KeyStoreFilterIterator(Iterator<KeyStoreEntry> iterator, KeyStoreFilter filter) {
		this.iterator = iterator;
		this.filter = filter;
	}

	@Override
	public boolean hasNext() {
		while ((next == null) && iterator.hasNext()) {
			KeyStoreEntry element = iterator.next();

			if ((filter == null) || filter.isExported(element)) {
				next = element;
			}
		}

		return next != null;
	}

	@Override
	public KeyStoreEntry next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}

		try {
			return next;
		} finally {
			next = null;
		}
	}
}
