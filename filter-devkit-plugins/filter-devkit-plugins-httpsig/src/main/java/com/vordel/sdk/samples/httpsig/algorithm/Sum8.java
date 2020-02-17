package com.vordel.sdk.samples.httpsig.algorithm;

import com.vordel.sdk.samples.httpsig.Checksum;

public class Sum8 implements Checksum {
	protected int value = 0;

	protected long value() {
		return value % 256;
	}

	@Override
	public void update(byte b) {
		value += b & 0xff;
	}

	@Override
	public void update(byte[] input) {
		update(input, 0, input.length);
	}

	@Override
	public void update(byte[] input, int offset, int len) {
		int value = this.value;

		for (int index = 0; index < len; index++) {
			value += input[offset + index] & 0xff;
		}

		this.value = value;
	}

	@Override
	public void reset() {
		value = 0;
	}

	@Override
	public byte[] sum() {
		try {
			byte[] sum = new byte[1];
			long value = value();

			sum[0] = (byte) value;

			return sum;
		} finally {
			reset();
		}
	}
}
