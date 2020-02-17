package com.vordel.sdk.samples.httpsig.algorithm;

public class Sum32 extends Sum8 {
	@Override
	protected long value() {
		return value % 0x100000000L;
	}

	@Override
	public byte[] sum() {
		try {
			byte[] sum = new byte[3];
			long value = value();

			sum[0] = (byte) (value >>> 24);
			sum[1] = (byte) (value >>> 16);
			sum[2] = (byte) (value >>> 8);
			sum[3] = (byte) value;

			return sum;
		} finally {
			reset();
		}
	}
}
