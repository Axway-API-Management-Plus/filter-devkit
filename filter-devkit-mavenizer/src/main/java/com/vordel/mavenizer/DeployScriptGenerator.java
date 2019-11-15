package com.vordel.mavenizer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.maven.repository.internal.ArtifactDescriptorUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;

import com.vordel.mavenizer.dist.GatewayRepoSys;
import com.vordel.mavenizer.dist.MavenizerRepository;

public class DeployScriptGenerator {
	public static void usage(String format, Object... args) {
		System.err.println(String.format(format, args));
		System.err.println("GatewayMavenizer <coords> <repositoryId> <url>");
		System.err.println("Ex: GatewayMavenizer com.axway.apigw:filter-deps-runtime:pom:7.5.3-8 id1 http://www.perdu.com/");
		System.exit(-1);
	}

	public static void main(String[] args) {
		if (args.length != 3) {
			usage("too few arguments");
		}

		Artifact artifact = new DefaultArtifact(args[0]);
		File root = new File("deploy").getAbsoluteFile();

		if (!root.exists()) {
			root.mkdir();
		}

		GatewayRepoSys sys = new GatewayRepoSys();
		RepositorySystemSession session = sys.getOfflineSession();
		RepositorySystem system = sys.getSystem();

		StringBuilder builder = new StringBuilder();

		builder.append("#!/bin/sh\n");

		try {
			CollectRequest collectRequest = new CollectRequest();

			collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));

			CollectResult collectResult = system.collectDependencies(session, collectRequest);
			DependencyRequest dependencyRequest = new DependencyRequest();

			dependencyRequest.setRoot(collectResult.getRoot());

			DependencyResult dependencyResult = system.resolveDependencies(session, dependencyRequest);

			for(ArtifactResult artifactResult : dependencyResult.getArtifactResults()) {
				if (artifactResult.isResolved()) {
					Artifact item = artifactResult.getArtifact();
					Artifact pom = null;

					try {
						if (!"pom".equals(item.getExtension())) {
							try {
								ArtifactResult pomResult = system.resolveArtifact(session, new ArtifactRequest().setArtifact(ArtifactDescriptorUtils.toPomArtifact(item)));

								if (pomResult.isResolved()) {
									pom = pomResult.getArtifact();
								}
							} catch (ArtifactResolutionException e) {
								/* ignore this */
							}
						}

						appendArtifact(root, builder, item, pom, args[1], args[2]);
					} catch (IOException e) {
						/* ignore this */
					}
				}
			}

			FileWriter writer = new FileWriter(new File(root, "deploy.sh"));

			try {
				writer.append(builder);
			} finally {
				writer.close();
			}			
		} catch (DependencyResolutionException e) {
			usage("got exception during dependency resolution");
		} catch (DependencyCollectionException e) {
			usage("got exception during dependency collection");
		} catch (IOException e) {
			usage("got exception writing deploy script");
		}
	}

	private static Artifact copyArtifact(Artifact artifact, File root) throws IOException {
		File in = artifact.getFile();
		File out = new File(new File(root, MavenizerRepository.sha1(in)), in.getName()).getAbsoluteFile();
		File parent = out.getParentFile();

		if (parent.exists()) {
			for(File file : parent.listFiles()) {
				file.delete();
			}

			parent.delete();
		}

		parent.mkdir();

		Files.copy(in.toPath(), out.toPath(), StandardCopyOption.COPY_ATTRIBUTES);

		return artifact.setFile(out);
	}

	private static void appendArtifact(File root, StringBuilder builder, Artifact artifact, Artifact pom, String repositoryId, String url) throws IOException {
		String extension = artifact.getExtension();

		if ("pom".equals(extension) || "jar".equals(extension)) {
			String classifier = artifact.getClassifier();

			artifact = copyArtifact(artifact, root);
			builder.append(String.format("mvn deploy:deploy-file -DgroupId=%s -DartifactId=%s -Dversion=%s ", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));

			if (!classifier.isEmpty()) {
				builder.append(String.format("-Dclassifier=%s ", classifier));
			}

			if (pom != null) {
				pom = copyArtifact(pom, root);

				builder.append(String.format("-DpomFile=\"%s\" ", pom.getFile()));
			} else {
			}

			builder.append(String.format("-Dpackaging=%s -Dfile=\"%s\" -DrepositoryId=%s -Durl=%s -DgeneratePom=false\n", extension, artifact.getFile(), repositoryId, url));
		} else {
			throw new IllegalStateException("unknown packaging");
		}
	}
}
