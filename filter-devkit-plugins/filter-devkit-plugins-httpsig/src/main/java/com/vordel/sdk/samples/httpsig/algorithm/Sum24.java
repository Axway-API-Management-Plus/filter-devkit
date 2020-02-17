package com.vordel.sdk.samples.httpsig.algorithm;

public class Sum24 extends Sum8 {
	@Override
	protected long value() {
		return value % 0x1000000;
	}

	@Override
	public byte[] sum() {
		try {
			byte[] sum = new byte[3];
			long value = value();

			sum[0] = (byte) (value >>> 16);
			sum[1] = (byte) (value >>> 8);
			sum[2] = (byte) value;

			return sum;
		} finally {
			reset();
		}
	}
}
