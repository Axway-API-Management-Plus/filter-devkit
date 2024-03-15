package com.vordel.circuit.filter.devkit.httpsig;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;

public class DigestOutputStream extends FilterOutputStream {
	private final Checksum algorithm;
	private byte[] digest = null;
	private int count = 0;

	public DigestOutputStream(MessageDigest algorithm) {
		this(asChecksum(algorithm));
	}

	public DigestOutputStream(final MessageDigest algorithm, OutputStream out) {
		this(asChecksum(algorithm), out);
	}

	public DigestOutputStream(Checksum algorithm) {
		this(algorithm, new OutputStream() {
			@Override
			public void write(int b) throws IOException {
			}

			@Override
			public void write(byte[] b) throws IOException {
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
			}

			@Override
			public void flush() throws IOException {
			}

			@Override
			public void close() throws IOException {
			}
		});
	}

	public DigestOutputStream(Checksum algorithm, OutputStream out) {
		super(out);

		this.algorithm = algorithm;
	}

	private static Checksum asChecksum(final MessageDigest algorithm) {
		return new Checksum() {
			@Override
			public void update(byte input) {
				algorithm.update(input);
			}

			@Override
			public void update(byte[] input, int offset, int len) {
				algorithm.update(input, offset, len);
			}

			@Override
			public void update(byte[] input) {
				algorithm.update(input);
			}

			@Override
			public byte[] sum() {
				return algorithm.digest();
			}

			@Override
			public void reset() {
				algorithm.reset();
			}
		};
	}

	public static <E> byte[] digest(Checksum algorithm, byte[] data) {
		DigestOutputStream out = new DigestOutputStream(algorithm);
		
		try {
			try {
				out.write(data);
			} finally {
				out.close();
			}
		} catch (IOException e) {
			throw new IllegalStateException("should not occur !");
		}

		return out.getDigest();
	}

	public static <E> byte[] digest(Checksum algorithm, EntityParser<E> parser, E entity) throws IOException {
		DigestOutputStream out = new DigestOutputStream(algorithm);

		try {
			parser.writeEntity(entity, out);
		} finally {
			out.close();
		}

		return out.getDigest();
	}

	public static <E> byte[] digest(MessageDigest algorithm, EntityParser<E> parser, E entity) throws IOException {
		DigestOutputStream out = new DigestOutputStream(algorithm);

		try {
			parser.writeEntity(entity, out);
		} finally {
			out.close();
		}

		return out.getDigest();
	}

	private void assertOpen() throws IOException {
		if (count < 0) {
			throw new IOException("stream is closed");
		}
	}

	public byte[] getDigest() {
		return count == 0 ? null : digest;
	}

	@Override
	public void write(int b) throws IOException {
		assertOpen();

		out.write(b);

		count += 1;
		algorithm.update((byte) b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		assertOpen();

		out.write(b);

		count += b.length;
		algorithm.update(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		assertOpen();

		out.write(b, off, len);

		count += len;
		algorithm.update(b, off, len);
	}

	@Override
	public void flush() throws IOException {
		assertOpen();

		super.flush();
	}

	@Override
	public void close() throws IOException {
		assertOpen();

		super.close();

		count = -1;
		digest = algorithm.sum();
	}
}
