package com.vordel.mavenizer.maps;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

import com.vordel.mavenizer.maps.BundleMap.Bundle;

public class BundleMap extends AbstractMap<String, Bundle> {
	private static final Pattern BUNDLEVERSION_MATCHER = Pattern.compile("([^\\.]+(?:\\.[^\\.]+(?:\\.[^\\.]+)?)?)(?:\\.(.+))?");

	private final Set<File> files;
	private final Set<Entry<String, Bundle>> entrySet = new AbstractSet<Entry<String, Bundle>>() {
		@Override
		public Iterator<Entry<String, Bundle>> iterator() {
			Iterator<Entry<String, Bundle>> iterator = null;

			if (files == null) {
				Set<Entry<String, Bundle>> empty = Collections.emptySet();

				iterator = empty.iterator();
			} else {
				iterator = new BundleIterator(files.iterator());
			}

			return iterator;
		}

		@Override
		public int size() {
			Iterator<Entry<String, Bundle>> iterator = iterator();
			int size = 0;

			while ((size < Integer.MAX_VALUE) && iterator.hasNext()) {
				iterator.next();
				size++;
			}

			return size;
		}
	};

	public BundleMap(Set<File> files) {
		this.files = files;
	}

	public Collection<Bundle> getBundlesByName(String name) {
		List<Bundle> result = new ArrayList<Bundle>();
		Iterator<Entry<String, Bundle>> iterator = entrySet().iterator();

		while (iterator.hasNext()) {
			Entry<String, Bundle> entry = iterator.next();
			Bundle bundle = entry.getValue();
			String cursor = bundle.getSymbolicName();

			if (name == null ? cursor == null : name.equals(cursor)) {
				result.add(bundle);
			}
		}

		return result;
	}

	@Override
	public Set<Entry<String, Bundle>> entrySet() {
		return entrySet;
	}

	public static Manifest extractManifest(ZipFile file, ZipEntry entry) throws IOException {
		InputStream is = file.getInputStream(entry);

		return readManifest(is);
	}

	public static Manifest readManifestFromRoot(File root) {
		File meta = new File(new File(root, "META-INF"), "MANIFEST.MF");
		Manifest manifest = null;

		if (meta.exists() && meta.isFile()) {
			try {
				manifest = readManifest(new FileInputStream(meta));
			} catch (IOException e) {
			}
		}

		return manifest;
	}

	private static Manifest readManifest(InputStream is) throws IOException {
		try {
			return new Manifest(is);
		} finally {
			is.close();
		}
	}

	private static String emptify(String value) {
		if ((value != null) && value.isEmpty()) {
			value = null;
		}

		return value;
	}

	public static Bundle asBundle(File file) {
		Bundle bundle = null;

		if (file.exists()) {
			if (file.isFile()) {
				try {
					ZipFile jar = new ZipFile(file);

					try {
						ZipEntry item = jar.getEntry("META-INF/MANIFEST.MF");

						if (item != null) {
							Manifest manifest = extractManifest(jar, item);

							bundle = asBundle(manifest, file);
						}
					} finally {
						jar.close();
					}
				} catch (IOException e) {
				}
			} else if (file.isDirectory()) {
				Manifest manifest = readManifestFromRoot(file);

				if (manifest != null) {
					bundle = asBundle(manifest, file);
				}

			}
		}

		return bundle;
	}

	private static Bundle asBundle(Manifest manifest, File file) {
		String name = null;
		String version = null;
		String qualifier = null;

		if (manifest != null) {
			Attributes attributes = manifest.getMainAttributes();

			name = emptify(attributes.getValue("Bundle-SymbolicName"));
			version = emptify(attributes.getValue("Bundle-Version"));

			if (name != null) {
				int index = name.indexOf(';');

				if (index > -1) {
					name = name.substring(0, index);
				}
			}

			if (version != null) {
				Matcher matcher = BUNDLEVERSION_MATCHER.matcher(version);

				if (matcher.matches()) {
					qualifier = emptify(matcher.group(2));
					version = emptify(matcher.group(1));
				} else {
					version = null;
				}
			}
		}

		return (name != null) && (version != null) ? new Bundle(name, version, qualifier, manifest, file) : null;
	}

	public static class BundleIterator implements Iterator<Entry<String, Bundle>> {
		private final Set<Bundle> seen = new HashSet<Bundle>();
		private final Iterator<File> iterator;

		private Bundle next = null;

		public BundleIterator(Iterator<File> iterator) {
			this.iterator = iterator;
		}

		@Override
		public boolean hasNext() {
			while ((next == null) && iterator.hasNext()) {
				File file = iterator.next();

				if (file.exists()) {
					Bundle bundle = asBundle(file);

					if (seen.add(bundle)) {
						next = bundle;
					}
				}
			}

			return next != null;
		}

		@Override
		public Entry<String, Bundle> next() {
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

	public static class Bundle implements Map.Entry<String, Bundle> {
		private final String name;
		private final String version;
		private final String qualifier;
		private final Manifest manifest;
		private final File file;

		private final Artifact artifact;

		public Bundle(String name, String version, String qualifier, Manifest manifest, File file) {
			this.name = name;
			this.version = version;
			this.qualifier = emptify(qualifier);
			this.manifest = manifest;
			this.file = file;

			this.artifact = new DefaultArtifact("", getSymbolicName(), "jar", getFullVersion()).setFile(file);
		}

		@Override
		public String getKey() {
			return ArtifactIdUtils.toId(artifact);
		}

		public String getSymbolicName() {
			return name;
		}

		public String getVersion() {
			return version;
		}

		public String getQualifier() {
			return qualifier;
		}

		public String getFullVersion() {
			StringBuilder builder = new StringBuilder();
			String qualifier = getQualifier();

			builder.append(getVersion());

			if (qualifier != null) {
				builder.append('.');
				builder.append(qualifier);
			}

			return builder.toString();
		}

		public Manifest getManifest() {
			return manifest;
		}

		public File getFile() {
			return file;
		}

		@Override
		public Bundle getValue() {
			return this;
		}

		@Override
		public Bundle setValue(Bundle value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int hashCode() {
			return getKey().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			boolean equals = obj == this;

			if ((!equals) && (obj instanceof Bundle)) {
				equals = getKey().equals(((Bundle) obj).getKey());
			}

			return equals;
		}

		@Override
		public String toString() {
			return getKey();
		}
	}
}
