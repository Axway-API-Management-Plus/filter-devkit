package com.vordel.circuit.filter.devkit.mavenizer;

import static com.vordel.circuit.filter.devkit.mavenizer.dist.AetherUtils.unduplicate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.repository.internal.ArtifactDescriptorUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.vordel.circuit.filter.devkit.mavenizer.dist.AetherUtils;
import com.vordel.circuit.filter.devkit.mavenizer.dist.Mavenizer;
import com.vordel.circuit.filter.devkit.mavenizer.dist.XPathContext;
import com.vordel.circuit.filter.devkit.mavenizer.maps.BundleMap;
import com.vordel.circuit.filter.devkit.mavenizer.maps.MavenMetaMap;
import com.vordel.circuit.filter.devkit.mavenizer.maps.BundleMap.Bundle;
import com.vordel.circuit.filter.devkit.mavenizer.maps.MavenMetaMap.MetaPOM;

public class MavenResult {
	private static final Comparator<Artifact> ARTIFACT_COMPARATOR = new Comparator<Artifact>() {
		@Override
		public int compare(Artifact o1, Artifact o2) {
			String name1 = o1.getArtifactId();
			String name2 = o2.getArtifactId();
			int result = name1.compareTo(name2);

			if (result == 0) {
				String classifier1 = o1.getClassifier();
				String classifier2 = o2.getClassifier();

				boolean hasClassifier1 = (classifier1 != null) && (!classifier1.isEmpty());
				boolean hasClassifier2 = (classifier2 != null) && (!classifier2.isEmpty());

				if (hasClassifier1 && hasClassifier2) {
					result = compareFull(o1, o2);
				} else if (hasClassifier1) {
					result = -1;
				} else if (hasClassifier2) {
					result = 1;
				} else {
					result = compareFull(o1, o2);
				}
			}

			return result;
		}

		private int compareFull(Artifact o1, Artifact o2) {
			String cmp1 = toCompareString(o1, true, true);
			String cmp2 = toCompareString(o2, true, true);

			return cmp1.compareTo(cmp2);
		}
	};

	private static final Comparator<Artifact> ARTIFACT_MATCH_LENGTH_COMPARATOR = new Comparator<Artifact>() {
		@Override
		public int compare(Artifact o1, Artifact o2) {
			String cmp1 = toCompareString(o1, false, false);
			String cmp2 = toCompareString(o2, false, false);

			int length1 = cmp1.length();
			int length2 = cmp2.length();

			return Integer.compare(length2, length1);
		}
	};
	private static final Comparator<Dependency> DEPENDENCY_COMPARATOR = new Comparator<Dependency>() {
		@Override
		public int compare(Dependency o1, Dependency o2) {
			Artifact artifact1 = o1.getArtifact();
			Artifact artifact2 = o2.getArtifact();

			return ARTIFACT_COMPARATOR.compare(artifact1, artifact2);
		}
	};

	private static final Comparator<Exclusion> EXCLUSION_COMPARATOR = new Comparator<Exclusion>() {
		@Override
		public int compare(org.apache.maven.model.Exclusion o1, org.apache.maven.model.Exclusion o2) {
			int result = o1.getArtifactId().compareTo(o2.getArtifactId());

			if (result == 0) {
				result = o1.getGroupId().compareTo(o2.getGroupId());
			}

			return result;
		}
	};

	private final Map<File, File> duplicates;

	private final Set<File> scanned;

	private final Set<String> resolved;

	private final Map<File, Dependency> dependencies;

	private final List<Dependency> managedDependencies;

	private final Map<String, CollectResult> collected;

	private final List<Artifact> probables;

	private final MavenMetaMap meta;

	public MavenResult(Set<File> scanned, Map<File, File> duplicates) {
		this.scanned = scanned;
		this.duplicates = duplicates;

		this.resolved = new HashSet<String>();
		this.dependencies = new HashMap<File, Dependency>();
		this.managedDependencies = new ArrayList<Dependency>();

		this.collected = new HashMap<String, CollectResult>();
		this.probables = new ArrayList<Artifact>();
		this.meta = new MavenMetaMap(scanned);
	}

	public Set<File> getScanned() {
		return scanned;
	}

	public Artifact find(String groupId, String artifactId, String classifier) {
		String key = toCompareString(new DefaultArtifact(groupId, artifactId, classifier, "jar", null), true, false);

		for (Dependency dependency : managedDependencies) {
			Artifact artifact = dependency.getArtifact();
			String cmp = toCompareString(artifact, true, false);

			if (key.equals(cmp)) {
				return artifact;
			}
		}

		return null;
	}

	public boolean isResolved(File file) {
		Dependency dependency = dependencies.get(unduplicate(duplicates, file));

		if (dependency != null) {
			Artifact artifact = dependency.getArtifact();
			Artifact pom = ArtifactDescriptorUtils.toPomArtifact(artifact);
			String coords = ArtifactIdUtils.toId(pom);

			return resolved.contains(coords);
		}

		return false;
	}

	public Artifact getArtifact(File file) {
		Dependency dependency = dependencies.get(unduplicate(duplicates, file));

		return dependency == null ? null : dependency.getArtifact();
	}

	public void resolveDependencies(Mavenizer mavenizer, RepositorySystemSession session) {
		RepositorySystem system = mavenizer.getSystem();

		for (Dependency dependency : dependencies.values()) {
			Artifact artifact = dependency.getArtifact();
			Artifact pom = ArtifactDescriptorUtils.toPomArtifact(artifact);

			ArtifactRequest request = new ArtifactRequest().setArtifact(pom);

			String coords = ArtifactIdUtils.toId(pom);
			boolean missing = !resolved.contains(coords);

			try {
				if (missing) {
					CollectResult collect = collected.get(coords);

					if (collect != null) {
						for (RemoteRepository repository : collect.getRoot().getRepositories()) {
							request.addRepository(repository);
						}
					} else {
						request.addRepository(AetherUtils.CENTRAL_REPOSITORY);
					}

					AetherUtils.setRemoteRepositoriesProxy(session, request.getRepositories());
					ArtifactResult result = system.resolveArtifact(session, request);

					if (!(missing = result.isMissing())) {
						resolved.add(coords);
					}
				}
			} catch (ArtifactResolutionException e) {
			}

			System.out.println(String.format("pom artifact '%s' %s", artifact, missing ? "unresolved" : "resolved"));
		}
	}

	public void collectDependencies(Mavenizer mavenizer, RepositorySystemSession session, boolean force) {
		if (force) {
			for (Dependency managed : managedDependencies) {
				Artifact artifact = managed.getArtifact();
				String coords = ArtifactIdUtils.toId(ArtifactDescriptorUtils.toPomArtifact(artifact));

				collected.remove(coords);
			}
		}

		collectDependencies(mavenizer, session);
	}

	public void collectDependencies(Mavenizer mavenizer, RepositorySystemSession session) {
		RepositorySystem system = mavenizer.getSystem();
		Deque<Dependency> work = new LinkedList<Dependency>();
		Dependency dependency = null;

		work.addAll(dependencies.values());

		while ((dependency = work.pollFirst()) != null) {
			Artifact artifact = dependency.getArtifact();
			String coords = ArtifactIdUtils.toId(ArtifactDescriptorUtils.toPomArtifact(artifact));

			CollectResult result = collected.get(coords);
			boolean resolvable = false;

			if (!collected.containsKey(coords)) {
				CollectRequest request = new CollectRequest();

				System.out.print(String.format("collecting dependencies for '%s'...", artifact.toString()));

				try {
					request.setRoot(dependency);
					request.setManagedDependencies(new ArrayList<Dependency>());

					for (Dependency managed : managedDependencies) {
						request.addManagedDependency(managed);
					}

					for (Artifact managed : probables) {
						request.addManagedDependency(new Dependency(managed, JavaScopes.COMPILE));
					}

					if (result == null) {
						request.addRepository(AetherUtils.CENTRAL_REPOSITORY);

						resolvable = true;

						try {
							AetherUtils.setRemoteRepositoriesProxy(session, request.getRepositories());

							result = system.collectDependencies(session, request);

							System.out.print("ok");
						} catch (DependencyCollectionException e) {
							DependencyNode root = (result = e.getResult()).getRoot();

							if (root == null) {
								result = null;
							}

							e.printStackTrace();

							System.out.print("failed");
						}
					}

					collected.put(coords, result);
				} finally {
					System.out.println();
				}

				if (result != null) {
					List<Artifact> collected = new ArrayList<Artifact>();
					DependencyNode root = result.getRoot();

					/*
					 * retrieve all collected dependencies (root only included if we have at least
					 * one child dependency)
					 */
					collect(root, collected, true);

					addArtifactDependencies(session, collected, work, resolvable);
				}
			}
		}
	}

	private static Document parsePOMFile(File pomFile) throws IOException {
		try {
			FileInputStream fileIS = new FileInputStream(pomFile);
			DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();

			builderFactory.setNamespaceAware(true);

			DocumentBuilder builder = builderFactory.newDocumentBuilder();

			return builder.parse(fileIS);
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException(e);
		} catch (SAXException e) {
			throw new IOException(e);
		}
	}

	private static XPath newXPath() {
		XPath xpath = XPathFactory.newInstance().newXPath();
		XPathContext context = new XPathContext();

		context.addNamespace("pom", "http://maven.apache.org/POM/4.0.0");
		xpath.setNamespaceContext(context);

		return xpath;
	}

	private static NodeList parseArtifactItems(XPath xpath, Document xmlDocument) throws XPathExpressionException {
		XPathExpression itemsExpression = xpath.compile("//pom:artifactItem");

		return (NodeList) itemsExpression.evaluate(xmlDocument, XPathConstants.NODESET);
	}

	private Set<File> getRemainingFiles() {
		HashSet<File> remaining = new HashSet<File>(scanned);

		remaining.removeAll(dependencies.keySet());

		return remaining;
	}

	public void addPOMArtifactItems(File pomFile) throws IOException {
		Set<File> remaining = getRemainingFiles();
		List<Artifact> matched = new ArrayList<Artifact>();

		try {
			Document xmlDocument = parsePOMFile(pomFile);
			XPath xpath = newXPath();

			XPathExpression groupIdExpression = xpath.compile("pom:groupId");
			XPathExpression artifactIdExpression = xpath.compile("pom:artifactId");
			XPathExpression classifierExpression = xpath.compile("pom:classifier");
			NodeList nodeList = parseArtifactItems(xpath, xmlDocument);

			for (int index = 0; index < nodeList.getLength(); index++) {
				Element item = (Element) nodeList.item(index);

				String groupId = (String) groupIdExpression.evaluate(item, XPathConstants.STRING);
				String artifactId = (String) artifactIdExpression.evaluate(item, XPathConstants.STRING);
				String classifier = (String) classifierExpression.evaluate(item, XPathConstants.STRING);

				if (groupId.indexOf("$") < 0) {
					for (File jar : remaining) {
						Artifact artifact = match(groupId, artifactId, classifier, jar);

						if (artifact != null) {
							matched.add(artifact);
						}
					}
				}
			}

		} catch (XPathExpressionException e) {
			throw new IllegalStateException(e);
		}

		probables.addAll(matched);

		/*
		 * remove duplicate groupIds/artifactIds and files
		 */
		removeMatchedDuplicates(matched, true);

		for (Artifact artifact : matched) {
			File jar = artifact.getFile();

			mapArtifact(jar, artifact);
		}

		removeManagedDuplicates();
	}

	public void mergeProbableDependencies() {
		List<Artifact> merged = new ArrayList<Artifact>(probables);

		removeMatchedDuplicates(merged, true);

		for (Artifact artifact : merged) {
			File jar = artifact.getFile();

			mapArtifact(jar, artifact);
		}

		removeManagedDuplicates();
	}

	public void addMetaDependencies(RepositorySystemSession session) {
		LocalRepositoryManager local = session.getLocalRepositoryManager();
		File base = local.getRepository().getBasedir();

		Set<File> remaining = getRemainingFiles();

		/*
		 * Step 1 : compute direct matches from embedded poms
		 */
		for (MetaPOM pom : meta.values()) {
			Artifact artifact = new DefaultArtifact(pom.getGroupId(), pom.getArtifactId(), pom.getClassifier(), "jar",
					pom.getVersion());
			File location = new File(base, local.getPathForLocalArtifact(artifact));
			String prefix = stripExtension(location.getName());

			Iterator<File> iterator = remaining.iterator();

			while (iterator.hasNext()) {
				File jar = iterator.next();
				String name = jar.getName();

				if (name.startsWith(prefix)) {
					String extension = extension(name);
					String suffix = String.format(".%s", extension);
					String classifier = name.substring(prefix.length(), name.lastIndexOf(suffix));

					if ((!classifier.isEmpty()) || (!"jar".equals(extension))) {
						/* refine artifact classifier and extension */
						artifact = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), classifier,
								extension, artifact.getVersion());
					}

					mapArtifact(jar, artifact);

					iterator.remove();
				}
			}
		}

		/*
		 * Step 2 : make a list of matches and sort them by artifact name length
		 * 
		 * dependency version can't be computed here since it requires the full POM
		 * hierarchy.
		 */
		for (MetaPOM pom : meta.values()) {
			Model model = pom.getModel();

			for (org.apache.maven.model.Dependency dependency : model.getDependencies()) {
				for (File jar : remaining) {
					Artifact matched = match(dependency, jar);

					if (matched != null) {
						probables.add(matched);
					}
				}
			}
		}

		removeManagedDuplicates();
	}

	private Dependency mapArtifact(File jar, Artifact artifact) {
		Dependency dependency = new Dependency(artifact.setFile(jar = unduplicate(duplicates, jar)),
				JavaScopes.COMPILE);

		System.out.println(String.format("mapping artifact : '%s' to '%s'", artifact.toString(), jar));

		dependencies.put(jar, dependency);
		managedDependencies.add(dependency);

		for (Entry<File, File> duplicate : duplicates.entrySet()) {
			if (duplicate.getValue().equals(jar)) {
				File file = duplicate.getKey();

				System.out.println(String.format("mapping duplicate : '%s' to '%s'", artifact.toString(), file));

				dependencies.put(file, dependency);
			}
		}

		return dependency;
	}

	public void addProbableArtifact(String groupId, String artifactId) {
		addProbableArtifact(groupId, artifactId, null);
	}

	public void addProbableArtifact(String groupId, String artifactId, String classifier) {
		Set<File> remaining = getRemainingFiles();

		for (File jar : remaining) {
			Artifact matched = match(groupId, artifactId, classifier, jar);

			if (matched != null) {
				probables.add(matched);
			}
		}

		removeMatchedDuplicates(probables, false);
	}

	private void addArtifactDependencies(RepositorySystemSession session, List<Artifact> artifacts,
			Deque<Dependency> work, boolean resolvable) {
		LocalRepositoryManager local = session.getLocalRepositoryManager();
		File base = local.getRepository().getBasedir();

		for (Artifact artifact : artifacts) {
			File location = new File(base, local.getPathForLocalArtifact(artifact));
			String name = location.getName();

			if (resolvable) {
				/* if collected from central, assume that dependency is already resolved */
				Artifact pom = ArtifactDescriptorUtils.toPomArtifact(artifact);
				String coords = ArtifactIdUtils.toId(pom);

				resolved.add(coords);
			}

			Set<File> remaining = getRemainingFiles();
			Set<File> matches = new HashSet<File>();

			for (File jar : remaining) {
				File key = unduplicate(duplicates, jar);

				if (name.equals(jar.getName())) {
					Dependency dependency = mapArtifact(jar, artifact);

					if (work != null) {
						work.offerLast(dependency);
						matches.add(key);
					}
				} else if (!matches.contains(key)) {
					Artifact matched = match(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(),
							jar);

					if (matched != null) {
						matches.add(key);
						probables.add(matched);
					}
				}
			}
		}

		removeManagedDuplicates();
	}

	private void removeMatchedDuplicates(List<Artifact> probables, boolean exact) {
		Collections.sort(probables, ARTIFACT_MATCH_LENGTH_COMPARATOR);

		Set<String> artifacts = new HashSet<String>();
		Set<File> files = new HashSet<File>();

		for (Dependency managed : dependencies.values()) {
			Artifact artifact = managed.getArtifact();
			String cmp = toCompareString(artifact, false, false);
			File jar = unduplicate(duplicates, artifact.getFile());

			artifacts.add(cmp);
			files.add(jar);
		}

		if (exact) {
			Set<String> seenArtifacts = new HashSet<String>();
			Set<File> seenFiles = new HashSet<File>();

			for (Artifact artifact : probables) {
				String cmp = toCompareString(artifact, false, false);
				File jar = unduplicate(duplicates, artifact.getFile());

				if (seenFiles.contains(jar) || seenArtifacts.contains(cmp)) {
					artifacts.add(cmp);
					files.add(jar);
				}

				seenArtifacts.add(cmp);
				seenFiles.add(jar);
			}
		}

		Iterator<Artifact> iterator = probables.iterator();

		while (iterator.hasNext()) {
			Artifact artifact = iterator.next();
			String cmp = toCompareString(artifact, false, false);
			File jar = unduplicate(duplicates, artifact.getFile());

			if (files.contains(jar) || artifacts.contains(cmp)) {
				iterator.remove();
			} else {
				artifacts.add(cmp);
				files.add(jar);
			}
		}
	}

	private static void collect(DependencyNode node, List<Artifact> collected, boolean root) {
		for (DependencyNode child : node.getChildren()) {
			if (root) {
				/*
				 * if we have at least one child, this is a valid collected pom. We can safely
				 * include root artifact
				 */
				collected.add(node.getArtifact());

				root = false;
			}

			collected.add(child.getArtifact());

			collect(child, collected, false);
		}
	}

	private Artifact match(org.apache.maven.model.Dependency dependency, File jar) {
		return match(dependency.getGroupId(), dependency.getArtifactId(), dependency.getClassifier(), jar);
	}

	private static String extension(String name) {
		int extension = name.lastIndexOf('.');

		return extension < 0 ? "jar" : name.substring(extension + 1);
	}

	private static String stripExtension(String name) {
		int extension = name.lastIndexOf('.');

		return extension < 0 ? name : name.substring(0, extension);
	}

	private Artifact match(String groupId, String artifactId, String classifier, File jar) {
		Artifact matched = null;
		String name = jar.getName();
		String extension = extension(name);

		String prefix = String.format("%s-", artifactId);
		String suffix = String.format(".%s", extension);

		if ((classifier != null) && (!classifier.isEmpty())) {
			suffix = String.format("-%s%s", classifier, suffix);
		}

		int index = name.lastIndexOf(suffix);

		if (name.startsWith(prefix) && (index > -1)) {
			String version = name.substring(prefix.length(), index);

			if (groupId.indexOf('$') < 0) {
				matched = new DefaultArtifact(groupId, artifactId, classifier, extension, version);
			}
		}

		return matched == null ? null : matched.setFile(unduplicate(duplicates, jar));
	}

	private static final String toCompareString(Artifact artifact, boolean useGroup, boolean useVersion) {
		StringBuilder builder = new StringBuilder();

		if (artifact != null) {
			String classifier = artifact.getClassifier();
			String extension = artifact.getExtension();

			builder.append(artifact.getArtifactId());

			if (useVersion) {
				String version = artifact.getVersion();

				if ((version != null) && (!version.isEmpty())) {
					builder.append('-');
					builder.append(version);
				}
			}

			if ((classifier != null) && (!classifier.isEmpty())) {
				builder.append('-');
				builder.append(classifier);
			}

			if ((extension != null) && (!extension.isEmpty())) {
				builder.append('.');
				builder.append(extension);
			}

			if (useGroup) {
				builder.append(':');
				builder.append(artifact.getGroupId());
			}
		}

		return builder.toString();
	}

	private void removeManagedDuplicates() {
		Collections.sort(managedDependencies, DEPENDENCY_COMPARATOR);

		Iterator<Dependency> iterator = managedDependencies.iterator();
		Artifact previous = null;

		while (iterator.hasNext()) {
			Dependency dependency = iterator.next();
			Artifact artifact = dependency.getArtifact();

			if ((previous != null) && ArtifactIdUtils.equalsVersionlessId(previous, artifact)) {
				System.out.println(
						String.format("duplicate artifact : '%s' and '%s'", previous.toString(), artifact.toString()));

				iterator.remove();
			} else {
				previous = artifact;
			}
		}

		removeMatchedDuplicates(probables, false);
	}

	private void fillDependencies(Map<String, org.apache.maven.model.Dependency> managed, Set<String> requested) {
		for (Dependency item : managedDependencies) {
			org.apache.maven.model.Dependency dependency = new org.apache.maven.model.Dependency();
			Artifact artifact = item.getArtifact();

			String key = toCompareString(artifact, true, false);
			File jar = artifact.getFile();

			if (isResolved(jar)) {
				String classifier = artifact.getClassifier();

				dependency.setGroupId(artifact.getGroupId());
				dependency.setArtifactId(artifact.getArtifactId());
				dependency.setVersion(artifact.getVersion());

				if ((classifier != null) && (!classifier.isEmpty())) {
					dependency.setClassifier(classifier);
				}

				managed.put(key, dependency);
			}

			requested.add(key);
		}
	}

	private void processManagedDependencies(Map<String, org.apache.maven.model.Dependency> managed, Map<String, Exclusion> excluded,
			Set<String> requested) {
		for (CollectResult collect : collected.values()) {
			if (collect != null) {
				DependencyNode root = collect.getRoot();

				Artifact artifact = root.getArtifact();
				String artifactKey = toCompareString(artifact, true, false);
				org.apache.maven.model.Dependency dependency = managed.get(artifactKey);

				if (dependency != null) {
					int exclusionCount = 0;

					for (DependencyNode node : root.getChildren()) {
						Dependency child = node.getDependency();

						Artifact exclude = child.getArtifact();
						String excludeKey = toCompareString(exclude, true, false);
						String scope = child.getScope();

						/* remove this artifact from list of direct dependencies */
						requested.remove(excludeKey);

						/*
						 * check if we need to exclude it (compile or runtime scope AND no managed
						 * dependency for it)
						 */
						if ((JavaScopes.COMPILE.equals(scope) || JavaScopes.RUNTIME.equals(scope))
								&& (managed.get(excludeKey) == null)) {
							List<Exclusion> exclusions = dependency.getExclusions();
							Exclusion exclusion = excluded.get(excludeKey);

							if (exclusion == null) {
								exclusion = new Exclusion();

								exclusion.setGroupId(exclude.getGroupId());
								exclusion.setArtifactId(exclude.getArtifactId());
								excluded.put(excludeKey, exclusion);
							}

							exclusions.add(exclusion);
							exclusionCount++;
						}
					}

					if (exclusionCount > 1) {
						Collections.sort(dependency.getExclusions(), EXCLUSION_COMPARATOR);
					}
				}
			}
		}
	}

	private void setModelManagedDependencies(Model target, Map<String, org.apache.maven.model.Dependency> managed) {
		DependencyManagement management = target.getDependencyManagement();

		if (management == null) {
			management = new DependencyManagement();

			target.setDependencyManagement(management);
		}

		List<org.apache.maven.model.Dependency> dependencies = management.getDependencies();

		for (Dependency item : managedDependencies) {
			String key = toCompareString(item.getArtifact(), true, false);
			org.apache.maven.model.Dependency dependency = managed.get(key);

			if (dependency != null) {
				dependencies.add(dependency);
			}
		}
	}

	private void setModelDependencies(Model target, Map<File, String> libs, Set<String> requested,
			boolean includeUnmanagedDependencies) {
		List<Artifact> artifacts = new ArrayList<Artifact>();
		Set<String> included = new HashSet<String>();

		for (File jar : scanned) {
			Artifact artifact = getArtifact(jar);

			if ((artifact != null) && included.add(toCompareString(artifact, true, false))) {
				artifacts.add(artifact);
			} else if (includeUnmanagedDependencies && (dependencies.get(jar) == null)) {
				String name = stripExtension(jar.getName());
				String version = "${project.version}";
				Bundle bundle = BundleMap.asBundle(jar);

				if (bundle != null) {
					name = bundle.getSymbolicName();
					version = bundle.getFullVersion();
				}

				artifact = new DefaultArtifact("${mavenized.unmanaged}", name, "jar", version).setFile(jar);
				artifacts.add(artifact);
			}
		}

		List<org.apache.maven.model.Dependency> dependencies = target.getDependencies();
		Collections.sort(artifacts, ARTIFACT_COMPARATOR);

		for (Artifact artifact : artifacts) {
			org.apache.maven.model.Dependency dependency = new org.apache.maven.model.Dependency();
			String classifier = artifact.getClassifier();
			File jar = artifact.getFile();

			dependency.setGroupId(artifact.getGroupId());
			dependency.setArtifactId(artifact.getArtifactId());

			if ((classifier != null) && (!classifier.isEmpty())) {
				dependency.setClassifier(classifier);
			}

			if (isResolved(jar)) {
				dependencies.add(dependency);
			} else if (libs != null) {
				String path = libs.get(jar);

				dependency.setVersion(artifact.getVersion());

				if ((path != null) && (!path.isEmpty())) {
					dependency.setScope(JavaScopes.SYSTEM);
					dependency.setSystemPath(String.format("${mavenized.distdir}/%s", path));
				}

				dependencies.add(dependency);
			}
		}

	}

	public void setModelDependencies(Mavenizer mavenizer, RepositorySystemSession session, Model target,
			Map<File, String> libs, boolean includeUnmanagedDependencies) {
		Map<String, org.apache.maven.model.Dependency> managed = new HashMap<String, org.apache.maven.model.Dependency>();
		Map<String, Exclusion> exclusions = new TreeMap<String, Exclusion>();
		Set<String> requested = new HashSet<String>();

		fillDependencies(managed, requested);
		processManagedDependencies(managed, exclusions, requested);

		setModelManagedDependencies(target, managed);
		setModelDependencies(target, libs, requested, includeUnmanagedDependencies);
	}

	private void excludeGradleArtifactFile(Map<File, String> libs, Set<String> excludes, Artifact artifact) {
		if (libs != null) {
			File jar = artifact.getFile();
			String path = libs.get(jar);

			if (path != null) {
				String version = artifact.getVersion();
				int index = path.lastIndexOf(version);

				if (index > -1) {
					String prefix = path.substring(0, index);
					String suffix = path.substring(index + version.length());

					path = String.format("%s*%s", prefix, suffix);
				}

				excludes.add(path);
			}
		}
	}

	private void excludeGradleFile(Map<File, String> libs, Set<String> excludes, File jar) {
		if (libs != null) {
			String path = libs.get(jar);

			if (path != null) {
				excludes.add(path);
			}
		}
	}

	private void expungeExcludedFiles(Set<String> excludedFiles) {
		Set<String> work = new HashSet<String>(excludedFiles);

		for(String path : work) {
			Set<String> matches = new HashSet<String>();
			int start = path.lastIndexOf('/');
			int version = path.lastIndexOf('*');

			if (version > -1) {
				String prefix = path.substring(start < 0 ? 0 : start + 1, version);
				String suffix = path.substring(version + 1, path.length());

				for(String match : excludedFiles) {
					int index = match.lastIndexOf('/');
					String name = match.substring(index < 0 ? 0 : index + 1, match.length());

					if (name.startsWith(prefix) && name.endsWith(suffix)) {
						matches.add(match);
					}
				}

				matches.remove(path);

				if (matches.size() > 0) {
					excludedFiles.removeAll(matches);
				}
			}
		}
	}

	public String printGradleDependencies(Map<File, String> libs) {
		StringBuilder dependencies = new StringBuilder();

		Map<String, org.apache.maven.model.Dependency> managed = new HashMap<String, org.apache.maven.model.Dependency>();
		Map<String, Exclusion> exclusions = new TreeMap<String, Exclusion>();
		Set<String> requested = new HashSet<String>();

		fillDependencies(managed, requested);
		processManagedDependencies(managed, exclusions, requested);

		Set<String> excludedFiles = new TreeSet<String>();

		for (File jar : duplicates.keySet()) {
			excludeGradleFile(libs, excludedFiles, jar);
		}

		for (Dependency item : managedDependencies) {
			Artifact artifact = item.getArtifact();
			String key = toCompareString(artifact, true, false);
			org.apache.maven.model.Dependency dependency = managed.get(key);

			if (dependency != null) {
				StringBuilder builder = new StringBuilder();
				String classifier = artifact.getClassifier();

				builder.append("    apigw_central('");
				builder.append(String.format("%s:%s:%s", artifact.getGroupId(), artifact.getArtifactId(),
						artifact.getVersion()));

				if ((classifier != null) && (!classifier.isEmpty())) {
					builder.append(String.format(":%s", classifier));
				}

				excludeGradleArtifactFile(libs, excludedFiles, artifact);
				builder.append("')");

				/* disable transitive resolution */
				builder.append(" {\n");
				builder.append("        transitive = false\n");
				builder.append("    }\n");

				dependencies.append(builder.toString());
			}
		}

		dependencies.append("    apigw_central fileTree(\"${apigw_vdistdir}\").matching {\n");

		expungeExcludedFiles(excludedFiles);

		for (String exclude : excludedFiles) {
			dependencies.append(String.format("        exclude '%s'\n", exclude));
		}

		dependencies.append("        include 'system/lib/**/*.jar'\n");
		dependencies.append("    }\n");
		
		return dependencies.toString();
	}

	public void printGradlePlatform(Map<File, String> libs) {
		System.out.println("dependencies {");

		Map<String, org.apache.maven.model.Dependency> managed = new HashMap<String, org.apache.maven.model.Dependency>();
		Map<String, Exclusion> exclusions = new TreeMap<String, Exclusion>();
		Set<String> requested = new HashSet<String>();

		fillDependencies(managed, requested);
		processManagedDependencies(managed, exclusions, requested);

		Set<String> excludedFiles = new TreeSet<String>();

		for (File jar : duplicates.keySet()) {
			excludeGradleFile(libs, excludedFiles, jar);
		}

		System.out.println("    constraints {");
		for (Dependency item : managedDependencies) {
			Artifact artifact = item.getArtifact();
			String key = toCompareString(artifact, true, false);
			org.apache.maven.model.Dependency dependency = managed.get(key);

			if (dependency != null) {
				StringBuilder builder = new StringBuilder();
				String classifier = artifact.getClassifier();

				builder.append("        implementation('");
				builder.append(String.format("%s:%s:%s", artifact.getGroupId(), artifact.getArtifactId(),
						artifact.getVersion()));

				if ((classifier != null) && (!classifier.isEmpty())) {
					builder.append(String.format(":%s", classifier));
				}

				excludeGradleArtifactFile(libs, excludedFiles, artifact);
				builder.append("')");

				List<Exclusion> excluded = dependency.getExclusions();

				if (!excluded.isEmpty()) {
					/* disable transitive resolution */
					builder.append(" {\n");

					for(Exclusion exclude : excluded) {
						builder.append(String.format("            exclude group: '%s', module: '%s'\n", exclude.getGroupId(), exclude.getArtifactId()));
					}

					builder.append("        }");
				}

				System.out.println(builder.toString());
			}
		}

		System.out.println("    }");
		System.out.println("    compileOnly fileTree(\"${apigw_vdistdir}\").matching {");

		expungeExcludedFiles(excludedFiles);

		for (String exclude : excludedFiles) {
			System.out.println(String.format("        exclude '%s'", exclude));
		}

		System.out.println("        include 'system/lib/**/*.jar'");
		System.out.println("    }");
		System.out.println("}");
	}
}
