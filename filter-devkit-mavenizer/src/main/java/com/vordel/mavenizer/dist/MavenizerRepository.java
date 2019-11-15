package com.vordel.mavenizer.dist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.repository.internal.ArtifactDescriptorUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.artifact.JavaScopes;

import com.vordel.mavenizer.maps.BundleMap;
import com.vordel.mavenizer.maps.MavenMetaMap;
import com.vordel.mavenizer.maps.MavenMetaMap.MetaPOM;

public class MavenizerRepository {
	public static RemoteRepository CENTRAL_REPOSITORY = getCentralRepository();

	private Comparator<Artifact> MATCH_COMPARATOR = new Comparator<Artifact>() {
		@Override
		public int compare(Artifact o1, Artifact o2) {
			String classifier1 = o1.getClassifier();
			String classifier2 = o2.getClassifier();

			boolean hasClassifier1 = (classifier1 != null) && (!classifier1.isEmpty());
			boolean hasClassifier2 = (classifier2 != null) && (!classifier2.isEmpty());
			int result = 0;

			if (hasClassifier1 && hasClassifier2) {
				String artifactId1 = o1.getArtifactId();
				String artifactId2 = o2.getArtifactId();

				result = Integer.compare(artifactId2.length(), artifactId1.length());

				if (result == 0) {
					result = artifactId1.compareTo(artifactId2);
				}
			} else if (hasClassifier1) {
				result = -1;
			} else if (hasClassifier2) {
				result = 1;
			} else {
				String artifactId1 = o1.getArtifactId();
				String artifactId2 = o2.getArtifactId();

				result = Integer.compare(artifactId2.length(), artifactId1.length());

				if (result == 0) {
					result = artifactId1.compareTo(artifactId2);
				}
			}

			return result;
		}
	};

	private static Comparator<SolrDocument> SOLRDOCUMENT_COMPARATOR = new Comparator<SolrDocument>() {
		@Override
		public int compare(SolrDocument o1, SolrDocument o2) {
			Long ts1 = (Long) o1.getFieldValue("timestamp");
			Long ts2 = (Long) o2.getFieldValue("timestamp");
			int result = 0;

			if ((ts1 == null) && (ts2 == null)) {
				result = 0;
			} else if (ts1 == null) {
				result = -1;
			} else if (ts2 == null) {
				result = 1;
			} else {
				result = Long.compare(ts1, ts2);
			}

			return result;
		}
	};

	private static Comparator<org.apache.maven.model.Dependency> DEPENDENCY_COMPARATOR = new Comparator<org.apache.maven.model.Dependency>() {
		@Override
		public int compare(org.apache.maven.model.Dependency o1, org.apache.maven.model.Dependency o2) {
			int result = o1.getArtifactId().compareTo(o2.getArtifactId());

			if (result == 0) {
				result = o1.getGroupId().compareTo(o2.getGroupId());
			}

			return result;
		}
	};

	private static Comparator<org.apache.maven.model.Exclusion> EXCLUSION_COMPARATOR = new Comparator<org.apache.maven.model.Exclusion>() {
		@Override
		public int compare(org.apache.maven.model.Exclusion o1, org.apache.maven.model.Exclusion o2) {
			int result = o1.getArtifactId().compareTo(o2.getArtifactId());

			if (result == 0) {
				result = o1.getGroupId().compareTo(o2.getGroupId());
			}

			return result;
		}
	};

	private final Map<String, CollectResult> collected = new HashMap<String, CollectResult>();

	private final Map<Artifact, Integer> counts = new HashMap<Artifact, Integer>();

	private static RemoteRepository getCentralRepository() {
		RemoteRepository.Builder builder = new RemoteRepository.Builder("central", "default", "http://repo.maven.apache.org/maven2");

		return builder.build();
	}

	private Artifact newArtifactFromId(String groupId, String artifactId, String classifier, String extension) {
		Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, null);
		Integer count = counts.get(artifact);
		Integer best = 0;

		if (count == null) {
			count = 0;
		}

		counts.put(artifact, count += 1);

		List<Map.Entry<Artifact, Integer>> matches = new ArrayList<Map.Entry<Artifact, Integer>>();

		for (Map.Entry<Artifact, Integer> entry : counts.entrySet()) {
			Artifact cursor = entry.getKey();

			if (cursor.getArtifactId().equals(artifact.getArtifactId()) && cursor.getClassifier().equals(artifact.getClassifier())) {
				if (best < entry.getValue()) {
					best = entry.getValue();
					artifact = cursor;
					matches.add(0, entry);
				} else {
					matches.add(entry);
				}
			}
		}

		if (matches.size() > 1) {
			System.out.println(String.format("artifact '%s' has multiple definitions", matches.toString()));
		}

		return artifact;
	}

	private Artifact newArtifactFromId(Artifact artifact) {
		return newArtifactFromId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), artifact.getExtension());
	}

	private final Map<String, Artifact> artifacts = new HashMap<String, Artifact>();
	private Set<Artifact> locked = new HashSet<Artifact>();

	private final Map<File, File> files = new HashMap<File, File>();

	public static Set<File> scan(File root, Set<File> scanned) {
		if ((root != null) && root.exists() && (scanned != null) && (!scanned.contains(root))) {
			if (root.isDirectory()) {
				File meta = new File(root, "META-INF");

				if (meta.exists() && meta.isDirectory()) {
					scanned.add(root);
				} else {
					for (File child : root.listFiles()) {
						if (child.isFile()) {
							scan(child, scanned);
						}
					}

					for (File child : root.listFiles()) {
						if (!child.isFile()) {
							scan(child, scanned);
						}
					}
				}
			} else if (root.isFile()) {
				try {
					ZipFile jar = new ZipFile(root);

					try {
						for (Enumeration<? extends ZipEntry> iterator = jar.entries(); iterator.hasMoreElements();) {
							ZipEntry item = iterator.nextElement();

							item.getName();
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

	private static String suffix(Artifact artifact, File file) {
		String classifier = artifact.getClassifier();
		String suffix = null;

		if (file.isDirectory()) {
			if ((classifier == null) || classifier.isEmpty()) {
				suffix = "";
			} else {
				suffix = String.format("-%s", classifier);
			}
		} else if (file.isFile()) {
			if ((classifier == null) || classifier.isEmpty()) {
				suffix = String.format(".%s", artifact.getExtension());
			} else {
				suffix = String.format("-%s.%s", classifier, artifact.getExtension());
			}
		}

		return suffix;
	}

	private static String extension(File file) {
		String extension = null;

		if (file.exists() && file.isFile()) {
			String name = file.getName();
			int index = name.lastIndexOf('.');

			if (index > -1) {
				extension = name.substring(index + 1, name.length());
			}
		}

		return extension;
	}

	private File file(File file) {
		File result = null;

		if (file != null) {
			result = files.get(file);

			if (result == null) {
				if (file.isDirectory()) {
					result = file;
				} else {
					try {
						Iterator<File> iterator = files.keySet().iterator();
						byte[] data = readFile(file);

						while ((result == null) && iterator.hasNext()) {
							File cursor = iterator.next();

							if (cursor.isFile()) {
								try {
									if (Arrays.equals(data, readFile(cursor))) {
										System.out.println(String.format("%s is duplicate of %s", file, cursor));

										result = cursor;
									}
								} catch (IOException e) {
								}
							}
						}

					} catch (IOException e) {
					} finally {
						if (result == null) {
							System.out.println(String.format("registering file %s", file));

							result = file;
						}
					}
				}

				files.put(file, result);
			}
		}

		return result;
	}

	public Artifact artifact(String groupId, String artifactId, String extension, String version, File file) {
		return artifact(groupId, artifactId, null, extension, version, file);
	}

	public Artifact artifact(String groupId, String artifactId, String classifier, String extension, String version, File file) {
		return artifact(new DefaultArtifact(groupId, artifactId, classifier, extension, version).setFile(file), true);
	}

	private Artifact artifact(Artifact artifact, boolean override) {
		if (artifact != null) {
			File file = file(artifact.getFile());

			if (file != null) {
				Artifact previous = findArtifact(file, override);

				if ((!locked.contains(previous)) && (override || (previous == null))) {
					String coords = ArtifactIdUtils.toId(artifact);

					artifacts.put(coords, artifact = artifact.setFile(file));
				} else {
					artifact = previous;
				}
			}
		}

		return artifact;
	}

	public Artifact match(String groupId, String artifactId, Set<File> scanned) {
		return match(groupId, artifactId, null, scanned);
	}

	public Artifact match(String groupId, String artifactId, String classifier, Set<File> scanned) {
		Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, "jar", null);

		return match(artifact, scanned);
	}
	
	public void register(Set<File> scanned) {
		Iterator<File> iterator = scanned.iterator();

		while (iterator.hasNext()) {
			file(iterator.next());
		}
	}

	public Artifact match(Artifact artifact, Set<File> scanned) {
		String prefix = String.format("%s-", artifact.getArtifactId());
		Iterator<File> iterator = scanned.iterator();

		while (iterator.hasNext()) {
			File file = iterator.next();
			String name = file.getName();
			boolean mapped = false;

			file(file);

			Artifact previous = findArtifact(file, false);

			if ((previous != null) && locked.contains(previous) && ArtifactIdUtils.equalsVersionlessId(previous, artifact)) {
				artifact = previous;
				mapped = true;
			} else if (name.startsWith(prefix)) {
				/* use maven conventions to detect */
				String suffix = suffix(artifact, file);

				if (suffix.isEmpty() || name.endsWith(suffix)) {
					boolean override = false;

					if ((previous == null) || (override = (MATCH_COMPARATOR.compare(artifact, previous) < 0))) {
						String version = suffix.isEmpty() ? name.substring(prefix.length()) : name.substring(prefix.length(), name.indexOf(suffix));

						artifact = newArtifactFromId(artifact).setVersion(version).setFile(file);

						if (override) {
							System.out.println(String.format("mapping file '%s' to artifact '%s' (better match)", file, artifact));
						} else if ((previous == null) || (!previous.equals(artifact))) {
							System.out.println(String.format("mapping file '%s' to artifact '%s'", file, artifact));
						}

						artifact = artifact(artifact, override);
					} else if ((previous != null) && ArtifactIdUtils.equalsVersionlessId(previous, artifact)) {
						artifact = newArtifactFromId(artifact).setVersion(previous.getVersion()).setFile(file);
					}

					mapped = true;
				}
			} else {
				mapped = findArtifact(file, false) != null;
			}

			if (!mapped) {
				BundleMap.Bundle bundle = BundleMap.asBundle(file);
				String artifactId = artifact.getArtifactId();

				if ((bundle != null)) {
					if (bundle.getSymbolicName().equals(artifactId)) {
						String groupId = artifact.getGroupId();
						String version = bundle.getVersion();
						String qualifier = bundle.getQualifier();
						String extension = extension(file);

						/* XXX disable version from POM (keep bundle version with qualifier) */
						// MetaPOM pom = MavenMetaMap.asMetaPOM(file, groupId, artifactId);

						if ((version != null) && (!version.isEmpty()) && (qualifier != null) && (!qualifier.isEmpty())) {
							if (artifact.getVersion().startsWith(version)) {
								if (artifact.getVersion().endsWith(qualifier)) {
									/* qualifier is part of maven version */
									version = artifact.getVersion();
									// } else if (artifact.getClassifier().isEmpty() && (pom != null)) {
									// /* drop qualifier if not part of artifact and not in version */
									// version = pom.getVersion();
								} else {
									version = String.format("%s.%s", version, qualifier);
								}

								qualifier = null;
							} else {
								String suffix = null;

								if (extension == null) {
									suffix = "";
								} else {
									suffix = String.format(".%s", extension);
								}

								// if ((artifact.getClassifier().isEmpty()) && (pom != null)) {
								// version = pom.getVersion();
								// qualifier = null;
								// } else
								if (name.endsWith(String.format("%s-%s%s", version, qualifier, suffix))) {
									/* default case, keep qualifier */
								} else if (name.endsWith(String.format("%s.%s%s", version, qualifier, suffix))) {
									/* qualifier is part of maven version */
									version = String.format("%s.%s", version, qualifier);
									qualifier = null;
								} else if (name.endsWith(String.format("-%s%s", version, suffix))) {
									/* disable qualifier */
									qualifier = null;
								}
							}
						}

						boolean override = false;

						if ((qualifier != null) && (!qualifier.isEmpty())) {
							artifact = new DefaultArtifact(groupId, artifactId, qualifier, extension == null ? "jar" : extension, version);

							System.out.println(String.format("mapping bundle '%s' to artifact '%s'", file, artifact));

							if ((previous == null) || (override = (MATCH_COMPARATOR.compare(artifact, previous) < 0))) {
								artifact(artifact.setFile(file), override);
							}
						} else if ((version != null) && (!version.isEmpty())) {
							artifact = new DefaultArtifact(groupId, artifactId, extension == null ? "jar" : extension, version);

							System.out.println(String.format("mapping bundle '%s' to artifact '%s'", file, artifact));

							if ((previous == null) || (override = (MATCH_COMPARATOR.compare(artifact, previous) < 0))) {
								artifact(artifact.setFile(file), override);
							}
						}
					}
				}
			}
		}

		return artifacts.get(ArtifactIdUtils.toId(artifact));
	}
	
	public Artifact findArtifact(File file) {
		return findArtifact(file, false);
	}

	private Artifact findArtifact(File file, boolean remove) {
		Artifact mapped = null;

		file = file(file);

		if (file != null) {
			Iterator<Artifact> iterator = artifacts.values().iterator();

			while ((mapped == null) && iterator.hasNext()) {
				Artifact artifact = iterator.next();

				if (artifact.getFile().equals(file)) {
					mapped = artifact;

					if (remove) {
						iterator.remove();
						locked.remove(artifact);
					}
				}
			}
		}

		return mapped;
	}

	public File findArtifact(Artifact artifact) {
		artifact = artifacts.get(ArtifactIdUtils.toId(artifact));

		return (artifact != null) ? artifact.getFile() : null;
	}

	public List<String> findVersions(String groupId, String artifactId, String extension) {
		return findVersions(groupId, artifactId, null, extension);
	}

	public List<String> findVersions(String groupId, String artifactId, String classifier, String extension) {
		Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, null);

		return findVersions(artifact);
	}

	public List<String> findVersions(Artifact artifact) {
		List<String> versions = new ArrayList<String>();

		for (Artifact art : artifacts.values()) {
			if (ArtifactIdUtils.equalsVersionlessId(artifact, art)) {
				versions.add(art.getVersion());
			}
		}

		return versions;
	}

	public Set<File> remaining(Set<File> out) {
		out.addAll(files.values());

		for (Artifact artifact : artifacts.values()) {
			out.remove(artifact.getFile());
		}

		return out;
	}

	public static String sha1(File file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			byte[] data = digest.digest(readFile(file));

			return toHexString(data);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Creates a hexadecimal representation of the specified bytes. Each byte is
	 * converted into a two-digit hex number and appended to the result with no
	 * separator between consecutive bytes.
	 * 
	 * @param bytes The bytes to represent in hex notation, may be be {@code null}.
	 * @return The hexadecimal representation of the input or {@code null} if the
	 *         input was {@code null}.
	 */
	private static String toHexString(byte[] bytes) {
		if (bytes == null) {
			return null;
		}

		StringBuilder buffer = new StringBuilder(bytes.length * 2);

		for (byte aByte : bytes) {
			int b = aByte & 0xFF;
			if (b < 0x10) {
				buffer.append('0');
			}
			buffer.append(Integer.toHexString(b));
		}

		return buffer.toString();
	}

	public void search(GatewayRepoSys sys, String url) throws SolrServerException, IOException {
		SolrServer server = sys.getSolrServer(url);
		Map<String, File> hashes = new HashMap<String, File>();

		for (File file : remaining(new HashSet<File>())) {
			if (file.exists() && file.isFile()) {
				hashes.put(sha1(file), file);
			}
		}

		for (Entry<String, File> entry : hashes.entrySet()) {
			File file = file(entry.getValue());

			if (file.exists() && file.isFile()) {
				searchArtifact(server, file, entry.getKey());
			}
		}
	}

	private void searchArtifact(SolrServer server, File file, String hash) throws SolrServerException, IOException {
		SolrParams query = new SolrQuery().set("q", String.format("1:\"%s\"", hash));
		QueryResponse response = server.query(query);
		SolrDocumentList results = response.getResults();

		System.out.print(String.format("lookup of '%s'...", file.getName()));

		try {
			if (results.getNumFound() > 0) {
				Artifact artifact = null;
				boolean lock = false;

				if (results.getNumFound() == 1) {
					SolrDocument result = results.get(0);

					String groupId = (String) result.getFieldValue("g");
					String artifactId = (String) result.getFieldValue("a");
					String version = (String) result.getFieldValue("v");
					String classifier = (String) result.getFieldValue("l");
					String extension = extension(file);
					String name = null;

					artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, version).setFile(file);

					if ((classifier == null) || (classifier.isEmpty())) {
						name = String.format("%s-%s.%s", artifactId, version, extension);
					} else {
						name = String.format("%s-%s-%s.%s", artifactId, version, classifier, extension);
					}

					for (Map.Entry<File, File> entry : files.entrySet()) {
						if (entry.getValue().equals(file)) {
							if (entry.getKey().getName().equalsIgnoreCase(name)) {
								lock |= true;
							}
						}
					}
				} else {
					Collections.sort(results, SOLRDOCUMENT_COMPARATOR);
					Iterator<SolrDocument> iterator = results.iterator();

					while ((artifact == null) && iterator.hasNext()) {
						SolrDocument result = iterator.next();
						String artifactId = (String) result.getFieldValue("a");
						String version = (String) result.getFieldValue("v");
						String classifier = (String) result.getFieldValue("l");
						String extension = extension(file);
						String name = null;

						if ((classifier == null) || (classifier.isEmpty())) {
							name = String.format("%s-%s.%s", artifactId, version, extension);
						} else {
							name = String.format("%s-%s-%s.%s", artifactId, version, classifier, extension);
						}

						for (Map.Entry<File, File> entry : files.entrySet()) {
							if (entry.getValue().equals(file)) {
								if (entry.getKey().getName().equalsIgnoreCase(name)) {
									String groupId = (String) result.getFieldValue("g");

									artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, version).setFile(file);
									lock |= true;
								}
							}
						}
					}
				}

				if (artifact != null) {
					Artifact previous = findArtifact(file, false);
					boolean override = false;

					if ((previous == null) || (override = (MATCH_COMPARATOR.compare(artifact, previous) < 0))) {
						artifact = artifact(artifact, override);

						if (lock) {
							locked.add(artifact);
						}
					}
				}

				System.out.print(String.format("found %d artifact%s", results.getNumFound(), results.getNumFound() > 1 ? "s" : ""));
			} else {
				System.out.print("not found");
			}
		} finally {
			System.out.println();
		}
	}

	public void collect(Map<String, MetaPOM> meta, RepositorySystem system, RepositorySystemSession session) {
		SortedSet<Artifact> hints = new TreeSet<Artifact>(MATCH_COMPARATOR);

		for (Artifact artifact : artifacts.values()) {
			if (!collected.containsKey(ArtifactIdUtils.toId(artifact))) {
				hint(hints, artifact);
			}
		}

		while (processHint(meta, hints, system, session))
			;
	}

	private boolean processHint(Map<String, MetaPOM> meta, SortedSet<Artifact> hints, RepositorySystem system, RepositorySystemSession session) {
		Iterator<Artifact> iterator = hints.iterator();
		boolean result = false;

		if (iterator.hasNext()) {
			Artifact next = iterator.next();

			/* remove hint before set gets modified */
			iterator.remove();

			if (result = (next != null)) {
				/* find the best match XXX */
				Artifact best = match(next, files.keySet());

				if (best != null) {
					Iterator<String> versions = findVersions(best).iterator();

					while (versions.hasNext()) {
						collect(meta, hints, system, session, best.setVersion(versions.next()).setFile(null).setProperties(null));
					}
				}
			}

		}

		if (!result) {
			iterator = artifacts.values().iterator();

			while ((!result) && iterator.hasNext()) {
				Artifact artifact = iterator.next();
				String coords = ArtifactIdUtils.toId(artifact);

				if (!collected.containsKey(coords)) {
					collect(meta, hints, system, session, artifact);

					result = true;
				}
			}
		}

		return result;
	}

	private void collect(Map<String, MetaPOM> meta, SortedSet<Artifact> hints, RepositorySystem system, RepositorySystemSession session, Artifact artifact) {
		/* retrieve meta model if any (this will give us remote repositories */
		String coords = ArtifactIdUtils.toId(ArtifactDescriptorUtils.toPomArtifact(artifact));
		MetaPOM pom = meta.get(coords);

		collect(hints, system, session, pom == null ? null : pom.getModel(), artifact);
	}

	private void collect(SortedSet<Artifact> hints, RepositorySystem system, RepositorySystemSession session, Model model, Artifact artifact) {
		String coords = ArtifactIdUtils.toId(artifact);
		CollectResult result = collected.get(coords);

		if (result == null) {
			System.out.println(String.format("collecting dependencies for '%s'", artifact.toString()));

			Dependency dependency = new Dependency(artifact, JavaScopes.COMPILE);
			Map<String, Repository> repositoryMap = new HashMap<String, Repository>();
			CollectRequest request = new CollectRequest();

			request.setRoot(dependency);

			if (model != null) {
				List<Repository> repositories = model.getRepositories();

				for (Repository repository : repositories) {
					repository = repository(repository);

					if (repository != null) {
						request.addRepository(ArtifactDescriptorUtils.toRemoteRepository(repository));

						repositoryMap.put(repository.getUrl(), repository);
					}
				}

				if (!request.getRepositories().isEmpty()) {
					try {
						result = system.collectDependencies(session, request);
					} catch (DependencyCollectionException e) {
						/* means repositories are invalid... */
					}
				}
			}

			if (result == null) {
				request.getRepositories().clear();
				request.addRepository(CENTRAL_REPOSITORY);

				try {
					result = system.collectDependencies(session, request);
				} catch (DependencyCollectionException e) {
					System.out.println(String.format("failed to collect dependencies for '%s'", artifact.toString()));
				}
			}

			collected.put(coords, result);

			if (result != null) {
				collect(hints, null, result.getRoot());
			}
		}
	}

	private static void collect(SortedSet<Artifact> hints, Artifact parent, DependencyNode node) {
		Artifact artifact = node.getArtifact();
		String scope = node.getDependency().getScope();

		if (JavaScopes.COMPILE.equals(scope) || JavaScopes.RUNTIME.equals(scope)) {
			hint(hints, artifact);

			for (DependencyNode child : node.getChildren()) {
				collect(hints, artifact, child);
			}
		}
	}

	private static void hint(SortedSet<Artifact> hints, Artifact artifact) {
		if (artifact != null) {
			artifact = artifact.setFile(null).setProperties(null).setVersion(null);

			hints.add(artifact);
		}
	}

	private static Repository repository(Repository repository) {
		if (repository != null) {
			String url = repository.getUrl();

			if ((url == null) || url.contains("${") || url.isEmpty()) {
				repository = null;
			} else if (url != null) {
				try {
					URI uri = new URI(url);

					if ("file".equalsIgnoreCase(uri.getScheme())) {
						repository = null;
					} else {
						String authority = uri.getAuthority();

						if (authority != null) {
							authority = authority.toLowerCase();

							if (authority.endsWith("java.net")) {
								repository = null;
							} else if (authority.endsWith("terracotta.org")) {
								repository = null;
							}
						}
					}
				} catch (URISyntaxException e) {
					repository = null;
				}
			}
		}

		return repository;
	}

	public void resolve(RepositorySystem system, RepositorySystemSession session) throws IOException {
		Set<Artifact> unresolved = new HashSet<Artifact>();

		for (Artifact artifact : artifacts.values()) {
			ArtifactRequest request = new ArtifactRequest().setArtifact(artifact.setFile(null));
			String coords = ArtifactIdUtils.toId(artifact);
			CollectResult collect = collected.get(coords);

			if (collect != null) {
				for (RemoteRepository repository : collect.getRoot().getRepositories()) {
					request.addRepository(repository);
				}
			} else {
				request.addRepository(CENTRAL_REPOSITORY);
			}

			try {
				system.resolveArtifact(session, request);

				System.out.println(String.format("artifact '%s' resolved", artifact));
			} catch (ArtifactResolutionException e) {
				System.out.println(String.format("artifact '%s' marked for install", artifact));

				unresolved.add(artifact);
			}
		}

		if (!unresolved.isEmpty()) {
			InstallRequest request = new InstallRequest();

			for (Artifact artifact : unresolved) {
				request.addArtifact(asFileArtifact(artifact));
			}

			try {
				system.install(session, request);

				System.out.println("Missing artifacts installed");
			} catch (InstallationException e) {
				System.out.println("Unable to install missing artifacts in local repository");
				e.printStackTrace();
			}
		}
	}
	
	private Artifact asFileArtifact(Artifact artifact) throws IOException {
		File file = artifact.getFile();

		if (file.isDirectory()) {
			File generated = createJarArchive(file);

			generated.deleteOnExit();

			findArtifact(file, true);
			artifact = artifact(artifact = artifact.setFile(file(generated)), true);

			files.put(file, generated);
		}
		
		return artifact;
	}

	public void install(RepositorySystem system, RepositorySystemSession session, String groupId, String artifactId, String extension, String version, File file) {
		install(system, session, groupId, artifactId, null, extension, version, file);
	}

	public void install(RepositorySystem system, RepositorySystemSession session, String groupId, String artifactId, String classifier, String extension, String version, File file) {
		Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, version).setFile(file);

		try {
			InstallRequest request = new InstallRequest().addArtifact(asFileArtifact(artifact));
			
			system.install(session, request);

			System.out.println(String.format("installed artifact '%s'", artifact));
		} catch (InstallationException e) {
			System.out.println(String.format("unable to install artifact '%s'", artifact));
		} catch (IOException e) {
			System.out.println(String.format("unable to install artifact '%s'", artifact));
		}
	}

	private static File createJarArchive(File root) throws IOException {
		File generated = File.createTempFile("artifact", ".jar");

		generated.deleteOnExit();

		FileOutputStream dst = new FileOutputStream(generated);

		try {
			Manifest manifest = BundleMap.readManifestFromRoot(root);
			JarOutputStream out = manifest == null ? new JarOutputStream(dst) : new JarOutputStream(dst, manifest);

			out.setLevel(9);

			try {
				addFile(out, root, root);
			} finally {
				out.close();
			}
		} finally {
			dst.close();
		}

		return generated;
	}

	private static void addFile(ZipOutputStream out, File root, File current) throws IOException {
		if (current.isFile()) {
			String path = root.toURI().relativize(current.toURI()).getPath();
			boolean archive = false;

			try {
				ZipFile jar = new ZipFile(current);

				try {
					ZipEntry item = jar.getEntry("META-INF/MANIFEST.MF");

					archive = item == null;
				} finally {
					jar.close();
				}
			} catch (IOException e) {
				archive = !"META-INF/MANIFEST.MF".equalsIgnoreCase(path);
			}

			if (archive) {
				byte[] blob = MavenMetaMap.readFile(current);
				ZipEntry entry = new ZipEntry(path);

				out.putNextEntry(entry);
				out.write(blob);
			}
		} else {
			File[] files = current.listFiles();

			for (File file : files) {
				addFile(out, root, file);
			}
		}
	}

	public void unresolvable(Set<File> scanned) {
		Set<File> remaining = new HashSet<File>(scanned);

		for (Artifact artifact : artifacts.values()) {
			File file = artifact.getFile();

			remaining.remove(file);

			for (Map.Entry<File, File> entry : files.entrySet()) {
				if (entry.getValue().equals(file)) {
					remaining.remove(entry.getKey());
				}
			}
		}

		for (File file : remaining) {
			System.out.println(String.format("file '%s' is not an artifact", file));
		}
	}

	public void setDependencies(RepositorySystem system, RepositorySystemSession session, Model target, Set<File> scanned, boolean all) {
		Map<String, MetaPOM> metaPoms = new HashMap<String, MetaPOM>(new MavenMetaMap(scanned));

		/*
		 * The excluded map contains the list of computed exclusions. It is built in two
		 * steps. The requested map contains the list of dependencies that needs to be
		 * declared at the top level.
		 */
		Map<String, Set<Exclusion>> excluded = new HashMap<String, Set<Exclusion>>();
		Set<Artifact> requested = new HashSet<Artifact>();

		for (File file : scanned) {
			Artifact artifact = findArtifact(file, false);

			if (artifact != null) {
				requested.add(artifact);
			}
		}

		Set<Artifact> filtered = new HashSet<Artifact>(requested);

		for (Artifact artifact : filtered) {
			CollectResult collect = collected.get(ArtifactIdUtils.toId(artifact));

			if (collect != null) {
				/* reject all transitive dependencies whenever they come from */
				rejectDependencies(excluded, requested, null, collect.getRoot());
			}
		}

		for (Artifact artifact : filtered) {
			CollectResult collect = collected.get(ArtifactIdUtils.toId(artifact));

			if (collect != null) {
				/* expunge exclusion map from collected dependencies */
				processDependencies(excluded, requested, null, collect.getRoot());
			}
		}

		for (Entry<String, Set<Exclusion>> entry : excluded.entrySet()) {
			/*
			 * Additional step for fine grained requested dependencies. The above step
			 * detected requested dependencies using artifact version matching. This step
			 * will detect dependencies exclusions.
			 */
			if (!entry.getValue().isEmpty()) {
				String coords = entry.getKey();
				Artifact artifact = artifacts.get(coords);

				requested.add(artifact);
			}
		}

		DependencyManagement managed = target.getDependencyManagement();

		if (managed == null) {
			managed = new DependencyManagement();

			target.setDependencyManagement(managed);
		}

		Set<String> repositoryIds = new HashSet<String>();

		List<org.apache.maven.model.Dependency> dependencies = target.getDependencies();
		List<org.apache.maven.model.Dependency> managedDependencies = managed.getDependencies();
		List<Repository> repositories = target.getRepositories();
		
		repositories.clear();

		for (Artifact artifact : filtered) {
			org.apache.maven.model.Dependency dependency = new org.apache.maven.model.Dependency();
			String classifier = artifact.getClassifier();

			dependency.setGroupId(artifact.getGroupId());
			dependency.setArtifactId(artifact.getArtifactId());
			dependency.setVersion(artifact.getVersion());

			if ((classifier != null) && (!classifier.isEmpty())) {
				dependency.setClassifier(classifier);
			}

			String coords = ArtifactIdUtils.toId(artifact);
			Set<Exclusion> exclusions = excluded.get(coords);

			if (exclusions != null) {
				List<org.apache.maven.model.Exclusion> list = dependency.getExclusions();

				for (Exclusion item : exclusions) {
					org.apache.maven.model.Exclusion exclusion = new org.apache.maven.model.Exclusion();

					exclusion.setGroupId(item.getGroupId());
					exclusion.setArtifactId(item.getArtifactId());

					list.add(exclusion);
				}

				Collections.sort(list, EXCLUSION_COMPARATOR);
			}

			managedDependencies.add(dependency);

			if (all || requested.contains(artifact)) {
				CollectResult collect = collected.get(ArtifactIdUtils.toId(artifact));

				if (collect != null) {
					MetaPOM pom = metaPoms.get(ArtifactIdUtils.toId(ArtifactDescriptorUtils.toPomArtifact(artifact)));

					if (pom != null) {
						for (Repository repository : pom.getModel().getRepositories()) {
							repository = repository(repository);

							if (repository != null) {
								String id = repository.getId();

								if (repositoryIds.add(id)) {
									for (RemoteRepository remote : collect.getRoot().getRepositories()) {
										if (remote.getId().equals(repository.getId())) {
											repositories.add(repository);
										}
									}
								}
							}
						}
					}
				}

				dependency = new org.apache.maven.model.Dependency();

				dependency.setGroupId(artifact.getGroupId());
				dependency.setArtifactId(artifact.getArtifactId());

				if ((classifier != null) && (!classifier.isEmpty())) {
					dependency.setClassifier(classifier);
				}

				dependencies.add(dependency);
			}
		}

		Collections.sort(dependencies, DEPENDENCY_COMPARATOR);
		Collections.sort(managedDependencies, DEPENDENCY_COMPARATOR);
	}

	private void rejectDependencies(Map<String, Set<Exclusion>> excluded, Set<Artifact> requested, Artifact parent, DependencyNode node) {
		Artifact artifact = node.getArtifact();
		String scope = node.getDependency().getScope();

		if (JavaScopes.COMPILE.equals(scope) || JavaScopes.RUNTIME.equals(scope)) {
			File match = findArtifact(artifact);

			if (match == null) {
				Iterator<String> versions = findVersions(artifact).iterator();

				if (versions.hasNext()) {
					String version = versions.next();

					match = findArtifact(artifact.setVersion(version));
				}
			}

			if (match != null) {
				artifact = findArtifact(match, false);
				node = collected.get(ArtifactIdUtils.toId(artifact)).getRoot();

				if (parent != null) {
					requested.remove(artifact);
				}
			}

			if (parent != null) {
				Exclusion exclusion = new Exclusion(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), artifact.getExtension());
				String coords = ArtifactIdUtils.toId(parent);
				Set<Exclusion> exclusions = excluded.get(coords);

				if (exclusions == null) {
					exclusions = new HashSet<Exclusion>();

					excluded.put(coords, exclusions);
				}

				exclusions.add(exclusion);
			}

			for (DependencyNode child : node.getChildren()) {
				rejectDependencies(excluded, requested, artifact, child);
			}
		}
	}

	private void processDependencies(Map<String, Set<Exclusion>> excluded, Set<Artifact> requested, Artifact parent, DependencyNode node) {
		Artifact artifact = node.getArtifact();
		String scope = node.getDependency().getScope();

		if (JavaScopes.COMPILE.equals(scope) || JavaScopes.RUNTIME.equals(scope)) {
			File match = findArtifact(artifact);
			boolean request = false;

			if (match == null) {
				Iterator<String> versions = findVersions(artifact).iterator();

				if (versions.hasNext()) {
					String version = versions.next();

					match = findArtifact(artifact.setVersion(version));
					request |= true;
				}
			}

			if (match != null) {
				artifact = findArtifact(match, false);
				node = collected.get(ArtifactIdUtils.toId(artifact)).getRoot();

				Exclusion exclusion = new Exclusion(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), artifact.getExtension());

				for (Set<Exclusion> exclusions : excluded.values()) {
					exclusions.remove(exclusion);
				}

				if (request) {
					requested.add(artifact);
				}

				for (DependencyNode child : node.getChildren()) {
					processDependencies(excluded, requested, artifact, child);
				}
			}
		}
	}
}
