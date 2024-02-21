package com.vordel.circuit.filter.devkit.mavenizer.dist;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;

public class AetherUtils {
	public static final RemoteRepository CENTRAL_REPOSITORY = getCentralRepository();

	private AetherUtils() {
	}

	private static RemoteRepository getCentralRepository() {
		RemoteRepository.Builder builder = new RemoteRepository.Builder("central", "default",
				"https://repo.maven.apache.org/maven2");

		return builder.build();
	}

	public static File findGlobalSettings() {
		return SettingsXmlConfigurationProcessor.DEFAULT_GLOBAL_SETTINGS_FILE;
	}

	public static String getMavenHome() {
		return SettingsXmlConfigurationProcessor.USER_MAVEN_CONFIGURATION_HOME.getAbsolutePath();
	}

	public static File findUserSettings() {
		return SettingsXmlConfigurationProcessor.DEFAULT_USER_SETTINGS_FILE;
	}

	public static void setRemoteRepositoriesProxy(RepositorySystemSession session,
			List<RemoteRepository> repositories) {
		ListIterator<RemoteRepository> iterator = repositories.listIterator();

		while (iterator.hasNext()) {
			RemoteRepository repository = iterator.next();
			RemoteRepository mirror = session.getMirrorSelector().getMirror(repository);

			if (mirror != null) {
				repository = mirror;

				iterator.set(repository);
			}

			Proxy proxy = session.getProxySelector().getProxy(repository);

			if (proxy != null) {
				/* XXX help proxy selector (does not seems to work out of the box) */
				repository = new RemoteRepository.Builder(repository).setProxy(proxy).build();

				iterator.set(repository);
			}
		}
	}

	private static boolean equals(File file1, File file2) throws IOException {
		RandomAccessFile raf1 = new RandomAccessFile(file1, "r");

		try {
			RandomAccessFile raf2 = new RandomAccessFile(file2, "r");

			try {
				if (raf1.length() != raf2.length()) {
					return false;
				}
				
				MappedByteBuffer buffer1 = raf1.getChannel().map(MapMode.READ_ONLY, 0, raf1.length());
				MappedByteBuffer buffer2 = raf2.getChannel().map(MapMode.READ_ONLY, 0, raf2.length());
				
				return buffer1.equals(buffer2);
			} finally {
				raf2.close();
			}
		} finally {
			raf1.close();
		}
	}

	public static Set<File> scan(File root, Set<File> scanned, Map<File, File> duplicates) {
		if ((root != null) && root.exists() && (scanned != null) && (!scanned.contains(root))) {
			if (root.isDirectory()) {
				File meta = new File(root, "META-INF");

				if (meta.exists() && meta.isDirectory()) {
					scanned.add(root);
				} else {
					for (File child : root.listFiles()) {
						if (!child.isFile()) {
							scan(child, scanned, duplicates);
						}
					}

					for (File child : root.listFiles()) {
						if (child.isFile()) {
							scan(child, scanned, duplicates);
						}
					}
				}
			} else if (isJavaArchive(root)) {
				try {
					ZipFile jar = new ZipFile(root);

					try {
						boolean duplicate = false;
						
						for (Enumeration<? extends ZipEntry> iterator = jar.entries(); iterator.hasMoreElements();) {
							ZipEntry item = iterator.nextElement();

							item.getName();
						}
						
						Iterator<File> iterator = scanned.iterator();
						
						while((!duplicate) && iterator.hasNext()) {
							File other = unduplicate(duplicates, iterator.next());
							
							if (duplicate = equals(other, root)) {
								System.out.println(String.format("%s is duplicate of %s", root, other));
								
								duplicates.put(root, other);
							}
						}

						scanned.add(root);
					} finally {
						jar.close();
					}
				} catch (ZipException e) {
				} catch (IOException e) {
				}
			}
		}

		return scanned;
	}
	
	public static File unduplicate(Map<File, File> duplicates, File file) {
		File found = duplicates.get(file);
		
		while(found != null) {
			file = found;
			found = duplicates.get(file);
		}
		
		return file;
	}

	private static boolean isJavaArchive(File root) {
		String name = root.getName();

		return root.isFile() && (name.endsWith(".jar") || name.endsWith(".zip"));

	}
}
