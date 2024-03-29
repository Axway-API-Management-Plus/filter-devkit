package com.vordel.circuit.filter.devkit.httpsig;

public interface Checksum {
	public void update(byte input);

	public void update(byte[] input);

	public void update(byte[] input, int offset, int len);

	public void reset();

	public byte[] sum();
}
