package com.vordel.circuit.filter.devkit.httpsig;

import java.util.List;

public interface HeaderParser<H> {
	public List<String> getHeaderValues(H headers, String name);
}
