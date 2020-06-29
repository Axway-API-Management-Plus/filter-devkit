package com.vordel.mavenizer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
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
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;

import com.vordel.mavenizer.dist.AetherUtils;
import com.vordel.mavenizer.dist.GatewayRepoSys;
import com.vordel.mavenizer.dist.MavenizerRepository;
import com.vordel.mavenizer.maps.MavenMetaMap;

public class InstallScriptGenerator {
	public static void usage(String format, Object... args) {
		System.err.println(String.format(format, args));
		System.err.println("InstallScriptGenerator <coords>");
		System.err.println("Ex: InstallScriptGenerator com.axway.apigw:filter-deps-runtime:pom:7.5.3-8");
		System.exit(-1);
	}

	public static void main(String[] args) {
		GatewayRepoSys sys = new GatewayRepoSys();
		Set<Artifact> artifacts = new HashSet<Artifact>();

		if (args.length == 0) {
			RepositorySystemSession session = sys.getOfflineSession();

			queryLocalVersions(session, new DefaultArtifact("com.axway.apigw", "filter-deps-runtime", null, "pom", null), artifacts);
			queryLocalVersions(session, new DefaultArtifact("com.axway.apigw", "filter-deps-studio", null, "pom", null), artifacts);
		} else if (args.length == 1) {
			artifacts.add(new DefaultArtifact(args[0]));
		} else {
			usage("too many arguments");
		}

		install(sys, artifacts);
	}

	public static void queryLocalVersions(RepositorySystemSession session, Artifact artifact, Set<Artifact> versions) {
		File root = session.getLocalRepository().getBasedir();

		StringBuilder path = new StringBuilder();

		path.append(artifact.getGroupId().replace('.', '/')).append('/');
		path.append(artifact.getArtifactId()).append('/');

		File base = new File(root, path.toString());

		if (base.exists() && base.isDirectory()) {
			for (File version : base.listFiles()) {
				if (version.exists() && version.isDirectory()) {
					versions.add(artifact.setVersion(version.getName()));
				}
			}
		}
	}

	public static void install(GatewayRepoSys sys, Set<Artifact> artifacts) {
		File root = new File("install").getAbsoluteFile();
		Set<Artifact> installed = new HashSet<Artifact>();
		Set<Artifact> poms = new HashSet<Artifact>();

		if (!root.exists()) {
			root.mkdir();
		}

		RepositorySystemSession session = sys.getOfflineSession();
		RepositorySystem system = sys.getSystem();
		StringBuilder builder = new StringBuilder();

		builder.append("#!/bin/sh\n");

		try {
			for (Artifact artifact : artifacts) {
				CollectRequest collectRequest = new CollectRequest();

				collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));

				CollectResult collectResult = system.collectDependencies(session, collectRequest);
				DependencyRequest dependencyRequest = new DependencyRequest();

				dependencyRequest.setRoot(collectResult.getRoot());

				if (resolveMissing(sys.getSession(), system, collectResult.getRoot())) {
					DependencyResult dependencyResult = system.resolveDependencies(session, dependencyRequest);

					for (ArtifactResult artifactResult : dependencyResult.getArtifactResults()) {
						if (artifactResult.isResolved()) {
							Artifact item = artifactResult.getArtifact();
							Artifact pom = null;

							if (installed.add(item)) {
								try {
									if (!"pom".equals(item.getExtension())) {
										try {
											ArtifactResult pomResult = system.resolveArtifact(session, new ArtifactRequest().setArtifact(ArtifactDescriptorUtils.toPomArtifact(item)));

											if (pomResult.isResolved()) {
												pom = pomResult.getArtifact();

												if (pom != null) {
													poms.add(pom);
												}
											}
										} catch (ArtifactResolutionException e) {
											/* ignore this */
										}
									} else {
										poms.add(item);
									}

									if ("jar".equals(item.getExtension())) {
										String classifier = item.getClassifier();

										if (!"sources".equals(classifier)) {
											try {
												ArtifactResult sourcesResult = system.resolveArtifact(session, new ArtifactRequest().setArtifact(new DefaultArtifact(item.getGroupId(), item.getArtifactId(), "sources", item.getExtension(), item.getVersion())));

												if (sourcesResult.isResolved()) {
													appendArtifact(root, builder, sourcesResult.getArtifact(), null);
												}
											} catch (ArtifactResolutionException e) {
												/* ignore this */
											}
										}

										if (!"javadoc".equals(classifier)) {
											try {
												ArtifactResult sourcesResult = system.resolveArtifact(session, new ArtifactRequest().setArtifact(new DefaultArtifact(item.getGroupId(), item.getArtifactId(), "javadoc", item.getExtension(), item.getVersion())));

												if (sourcesResult.isResolved()) {
													appendArtifact(root, builder, sourcesResult.getArtifact(), null);
												}
											} catch (ArtifactResolutionException e) {
												/* ignore this */
											}
										}
									}

									appendArtifact(root, builder, item, pom);
								} catch (IOException e) {
									/* ignore this */
								}
							}
						}
					}
				}
			}

			for(Artifact artifact : poms) {
				appendParent(session, system, root, builder, artifact, installed);
			}

			FileWriter writer = new FileWriter(new File(root, "install.sh"));

			try {
				writer.append(builder);
			} finally {
				writer.close();
			}
		} catch (DependencyResolutionException e) {
			usage("got exception during dependency resolution");
		} catch (DependencyCollectionException e) {
			e.printStackTrace();
			usage("got exception during dependency collection");
		} catch (IOException e) {
			usage("got exception writing deploy script");
		}
	}

	private static void appendParent(RepositorySystemSession session, RepositorySystem system, File root, StringBuilder builder, Artifact artifact, Set<Artifact> installed) throws IOException {
		if ("pom".equals(artifact.getExtension())) {
			Model model = MavenMetaMap.parseModel(MavenizerRepository.readFile(artifact.getFile()));

			if (model != null) {
				Parent parent = model.getParent();

				if (parent != null) {
					String groupId = parent.getGroupId();
					String artifactId = parent.getArtifactId();
					String version = parent.getVersion();

					if ((groupId != null) && (artifactId != null) && (version != null)) {
						ArtifactRequest request = new ArtifactRequest().setArtifact(new DefaultArtifact(groupId, artifactId, null, "pom", version));

						try {
							ArtifactResult result = system.resolveArtifact(session, request);


							if (result.isResolved()) {
								Artifact resolved = result.getArtifact();

								if (installed.add(resolved)) {
									appendArtifact(root, builder, resolved, null);
								}

								appendParent(session, system, root, builder, resolved, installed);
							}
						} catch (ArtifactResolutionException e) {
						}
					}
				}
			}
		}
	}

	private static boolean resolveMissing(RepositorySystemSession session, RepositorySystem system, DependencyNode node) {
		ArtifactRequest request = new ArtifactRequest();
		ArtifactResult result = null;

		try {
			request.setArtifact(node.getArtifact());
			result = system.resolveArtifact(session, request);
		} catch (ArtifactResolutionException ignore) {
		}

		if (result == null) {
			try {
				request.setRepositories(node.getRepositories());
				AetherUtils.setRemoteRepositoriesProxy(session, request.getRepositories());
				result = system.resolveArtifact(session, request);
			} catch (ArtifactResolutionException e) {
			}
		}

		if (result == null) {
			try {
				request.getRepositories().clear();
				request.addRepository(MavenizerRepository.CENTRAL_REPOSITORY);
				AetherUtils.setRemoteRepositoriesProxy(session, request.getRepositories());
				result = system.resolveArtifact(session, request);
			} catch (ArtifactResolutionException e) {
			}
		}

		if (result != null) {
			for (DependencyNode child : node.getChildren()) {
				resolveMissing(session, system, child);
			}
		}

		return result != null;
	}

	private static Artifact copyArtifact(Artifact artifact, File root) throws IOException {
		File in = artifact.getFile();
		File out = new File(new File(root, MavenizerRepository.sha1(in)), in.getName()).getAbsoluteFile();
		File parent = out.getParentFile();

		if (parent.exists() && (!parent.isDirectory())) {
			parent.delete();
		}

		if (!parent.exists()) {
			parent.mkdir();
		}

		if (out.exists()) {
			out.delete();
		}

		Files.copy(in.toPath(), out.toPath(), StandardCopyOption.COPY_ATTRIBUTES);

		return artifact.setFile(out);
	}

	private static void appendArtifact(File root, StringBuilder builder, Artifact artifact, Artifact pom) throws IOException {
		String extension = artifact.getExtension();
		URI uri = root.toURI();

		if ("pom".equals(extension) || "jar".equals(extension) || "zip".equals(extension)) {
			String classifier = artifact.getClassifier();

			artifact = copyArtifact(artifact, root);
			builder.append(String.format("mvn install:install-file -DgroupId=%s -DartifactId=%s -Dversion=%s ", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));

			if (!classifier.isEmpty()) {
				builder.append(String.format("-Dclassifier=%s ", classifier));
			}

			if (pom != null) {
				pom = copyArtifact(pom, root);

				builder.append(String.format("-DpomFile=\"%s\" ", uri.relativize(pom.getFile().toURI())));
			} else {
			}

			builder.append(String.format("-Dpackaging=%s -Dfile=\"%s\" -DgeneratePom=false\n", extension, uri.relativize(artifact.getFile().toURI())));
		} else {
			throw new IllegalStateException("unknown packaging");
		}
	}
}
