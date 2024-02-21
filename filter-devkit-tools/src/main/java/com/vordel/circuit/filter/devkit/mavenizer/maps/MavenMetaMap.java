package com.vordel.circuit.filter.devkit.mavenizer.maps;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.artifact.AbstractArtifact;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

import com.vordel.circuit.filter.devkit.mavenizer.maps.MavenMetaMap.MetaPOM;

public class MavenMetaMap extends AbstractMap<String, MetaPOM> {
	private static final Pattern MAVENMETA_MATCHER = Pattern.compile("(META-INF/maven/([^/]+)/([^/]+))/([^/]+)");

	private final Set<File> files;
	private final Set<Entry<String, MetaPOM>> entrySet = new AbstractSet<Entry<String, MetaPOM>>() {
		@Override
		public Iterator<Entry<String, MetaPOM>> iterator() {
			Iterator<Entry<String, MetaPOM>> iterator = null;

			if (files == null) {
				Set<Entry<String, MetaPOM>> empty = Collections.emptySet();

				iterator = empty.iterator();
			} else {
				iterator = new MetaPOMIterator(files.iterator());
			}

			return iterator;
		}

		@Override
		public int size() {
			Iterator<Entry<String, MetaPOM>> iterator = iterator();
			int size = 0;

			while ((size < Integer.MAX_VALUE) && iterator.hasNext()) {
				iterator.next();
				size++;
			}

			return size;
		}
	};

	public MavenMetaMap(Set<File> files) {
		this.files = files;
	}

	@Override
	public Set<Entry<String, MetaPOM>> entrySet() {
		return entrySet;
	}

	private static Properties extractProperties(ZipFile file, ZipEntry entry) throws IOException {
		InputStream is = file.getInputStream(entry);

		try {
			Properties props = new Properties();

			props.load(is);

			return props;
		} finally {
			is.close();
		}
	}

	public static byte[] extractFile(ZipFile file, ZipEntry entry) throws IOException {
		InputStream is = file.getInputStream(entry);

		return readFile(is);
	}

	public static byte[] readFile(InputStream is) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try {
			for (int read = -1; (read = is.read()) > -1;) {
				out.write(read);
			}

			return out.toByteArray();
		} finally {
			is.close();
		}
	}

	public static byte[] readFile(File file) throws IOException {
		byte[] out = null;

		if (file != null) {
			RandomAccessFile raf = new RandomAccessFile(file, "r");

			try {
				long size = raf.length();

				if (size > Integer.MAX_VALUE) {
					throw new UnsupportedOperationException();
				}

				out = new byte[(int) size];

				long read = raf.read(out);

				if (read < size) {
					throw new IllegalStateException();
				}
			} finally {
				raf.close();
			}
		}

		return out;
	}

	public static Model parseModel(byte[] binary) throws IOException {
		Model model = null;

		if (binary != null) {
			InputStream is = new ByteArrayInputStream(binary);

			try {
				MavenXpp3Reader reader = new MavenXpp3Reader();

				model = reader.read(is);
			} catch (XmlPullParserException e) {
				throw new IOException(e);
			} finally {
				is.close();
			}
		}

		return model;
	}

	public static MetaPOM asMetaPOM(File root, String groupId, String artifactId) {
		MetaPOM pom = null;

		if (root.exists()) {
			try {
				if (root.isDirectory()) {
					File maven = new File(new File(root, "META-INF"), "maven");
					File groupDirectory = new File(maven, groupId);
					File artifactDirectory = new File(groupDirectory, artifactId);

					File modelFile = new File(artifactDirectory, "pom.xml");
					File modelProperties = new File(artifactDirectory, "pom.properties");

					if (modelFile.exists() && modelProperties.exists() && modelFile.isFile() && modelProperties.isFile()) {
						FileInputStream is = new FileInputStream(modelProperties);
						Properties props = new Properties();

						try {
							props.load(is);
						} finally {
							is.close();
						}

						pom = new MetaPOM(props, parseModel(readFile(modelFile)));
					}
				} else if (root.isFile()) {
					ZipFile jar = new ZipFile(root);

					try {
						String path = String.format("META-INF/maven/%s/%s", groupId, artifactId);
						ZipEntry modelFile = jar.getEntry(String.format("%s/pom.xml", path));
						ZipEntry modelProperties = jar.getEntry(String.format("%s/pom.properties", path));

						if ((modelFile != null) && (modelProperties != null)) {
							pom = new MetaPOM(extractProperties(jar, modelProperties), parseModel(extractFile(jar, modelFile)));
						}
					} finally {
						jar.close();
					}
				}
			} catch (IOException e) {
			}
		}

		return pom;
	}

	public static class MetaPOMIterator implements Iterator<Entry<String, MetaPOM>> {
		private final Set<Entry<String, MetaPOM>> pending = new HashSet<Entry<String, MetaPOM>>();
		private final Set<Artifact> seen = new HashSet<Artifact>();
		private final Iterator<File> iterator;

		private Entry<String, MetaPOM> next = null;

		public MetaPOMIterator(Iterator<File> iterator) {
			this.iterator = iterator;
		}

		@Override
		public boolean hasNext() {
			boolean hasNext = true;

			while ((next == null) && hasNext) {
				if (!pending.isEmpty()) {
					next = nextPending();
				} else if (iterator.hasNext()) {
					File file = iterator.next();

					if (file.exists()) {
						if (file.isDirectory()) {
							File maven = new File(new File(file, "META-INF"), "maven");

							if (maven.exists() && maven.isDirectory()) {
								for (File groupDirectory : maven.listFiles()) {
									for (File artifactDirectory : groupDirectory.listFiles()) {
										File modelFile = new File(artifactDirectory, "pom.xml");
										File modelProperties = new File(artifactDirectory, "pom.properties");

										try {
											if (modelFile.exists() && modelProperties.exists()) {
												FileInputStream is = new FileInputStream(modelProperties);
												Properties props = new Properties();

												try {
													props.load(is);
												} finally {
													is.close();
												}

												pending.add(new MetaPOM(props, parseModel(readFile(modelFile))));
											}
										} catch (IOException e) {
											/* ignore */
										}
									}
								}
							}
						} else if (file.isFile()) {
							try {
								ZipFile jar = new ZipFile(file);

								try {
									Map<String, byte[]> modelMap = new HashMap<String, byte[]>();
									Map<String, Properties> propsMap = new HashMap<String, Properties>();

									for (Enumeration<? extends ZipEntry> iterator = jar.entries(); iterator.hasMoreElements();) {
										try {
											ZipEntry item = iterator.nextElement();
											String name = item.getName();

											Matcher matcher = MAVENMETA_MATCHER.matcher(name);

											if (matcher.matches()) {
												String path = matcher.group(1);
												String entry = matcher.group(4);

												if ("pom.xml".equals(entry)) {
													modelMap.put(path, extractFile(jar, item));
												} else if ("pom.properties".equals(entry)) {
													propsMap.put(path, extractProperties(jar, item));
												}
											}
										} catch (IOException e) {
										}
									}

									for (String path : modelMap.keySet()) {
										Properties props = propsMap.get(path);
										byte[] data = modelMap.get(path);

										if ((props != null) && (data != null)) {
											pending.add(new MetaPOM(props, parseModel(data)));
										}
									}
								} finally {
									jar.close();
								}
							} catch (IOException e) {
							}
						}
					}
				} else {
					hasNext &= false;
				}
			}

			return next != null;
		}

		private Entry<String, MetaPOM> nextPending() {
			Iterator<Entry<String, MetaPOM>> iterator = pending.iterator();
			Entry<String, MetaPOM> next = null;

			while ((next == null) && iterator.hasNext()) {
				next = iterator.next();
				iterator.remove();

				if (!seen.add(next.getValue().setFile(null).setProperties(null))) {
					next = null;
				}
			}

			return next;
		}

		@Override
		public Entry<String, MetaPOM> next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			try {
				return next;
			} finally {
				next = null;
			}
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	public static class MetaPOM extends AbstractArtifact implements Map.Entry<String, MetaPOM> {
		private final Properties properties;
		private final Model model;

		public MetaPOM(Properties properties, Model model) {
			this.properties = properties;
			this.model = model;
		}

		@Override
		public String getKey() {
			return ArtifactIdUtils.toId(this);
		}

		@Override
		public MetaPOM getValue() {
			return this;
		}

		@Override
		public MetaPOM setValue(MetaPOM value) {
			throw new UnsupportedOperationException();
		}

		public Model getModel() {
			return model;
		}

		@Override
		public String getGroupId() {
			return properties == null ? null : properties.getProperty("groupId");
		}

		@Override
		public String getArtifactId() {
			return properties == null ? null : properties.getProperty("artifactId");
		}

		@Override
		public String getVersion() {
			return properties == null ? null : properties.getProperty("version");
		}

		@Override
		public String getClassifier() {
			return "";
		}

		@Override
		public String getExtension() {
			return "pom";
		}

		@Override
		public File getFile() {
			return null;
		}

		@Override
		public Map<String, String> getProperties() {
			return Collections.emptyMap();
		}
	}
}
