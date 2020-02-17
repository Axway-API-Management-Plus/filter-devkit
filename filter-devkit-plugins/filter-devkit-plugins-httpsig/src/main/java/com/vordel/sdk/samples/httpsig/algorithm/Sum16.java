package com.vordel.sdk.samples.httpsig.algorithm;

public class Sum16 extends Sum8 {
	@Override
	protected long value() {
		return value % 0x10000;
	}

	@Override
	public byte[] sum() {
		try {
			byte[] sum = new byte[2];
			long value = value();

			sum[0] = (byte) (value >>> 8);
			sum[1] = (byte) value;

			return sum;
		} finally {
			reset();
		}
	}
}
