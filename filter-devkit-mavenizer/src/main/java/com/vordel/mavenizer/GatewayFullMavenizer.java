package com.vordel.mavenizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.solr.client.solrj.SolrServerException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

import com.vordel.mavenizer.dist.GatewayRepoSys;
import com.vordel.mavenizer.dist.MavenizerRepository;
import com.vordel.mavenizer.maps.MavenMetaMap;
import com.vordel.mavenizer.maps.MavenMetaMap.MetaPOM;

public class GatewayFullMavenizer {
	public static void usage(String format, Object... args) {
		System.err.println(String.format(format, args));
		System.err.println("GatewayMavenizer <vdistdir> [policystudiodir]");
		System.exit(-1);
	}

	private static File scanGateway(File root, Set<File> files) {
		if ((!root.exists()) || (!root.isDirectory())) {
			usage("'%s' is not a directory or does not exist", root);
		}

		File libs = new File(new File(root, "system"), "lib");

		if ((!libs.exists()) || (!libs.isDirectory())) {
			usage("'%s' is not a directory or does not exist", root);
		}

		MavenizerRepository.scan(libs, files);

		return libs;
	}

	private static void scanStudio(File plugins, Set<File> files) {
		File root = plugins.getParentFile();

		if ((!root.exists()) || (!root.isDirectory())) {
			usage("'%s' is not a directory or does not exist", root);
		}

		if ((!plugins.exists()) || (!plugins.isDirectory())) {
			usage("'%s' is not a directory or does not exist", root);
		}

		MavenizerRepository.scan(plugins, files);
	}

	public static void main(String[] args) {
		Set<File> gatewayFiles = new HashSet<File>();
		Set<File> studioFiles = new HashSet<File>();
		File libs = null;

		if (args.length == 1) {
			File root = new File(args[0]);

			libs = scanGateway(root, gatewayFiles);
		} else if (args.length == 2) {
			File gatewayRoot = new File(args[0]);
			File pluginRoot = new File(new File(args[1]), "plugins");

			libs = scanGateway(gatewayRoot, gatewayFiles);
			scanStudio(pluginRoot, studioFiles);
		} else {
			usage("Please provide API Gateway install directory");
		}

		try {
			GatewayRepoSys sys = new GatewayRepoSys();
			RepositorySystemSession session = sys.getSession();
			RepositorySystem system = sys.getSystem();

			MavenizerRepository repo = new MavenizerRepository();
			File gatewayRuntime = createServerRuntime(libs, gatewayFiles);
			String gatewayVersion = getGatewayVersion(repo, gatewayFiles);

			repo.artifact("com.axway.apigw", "filter-devkit-server-runtime", "jar", gatewayVersion, gatewayRuntime);

			Set<File> scanned = new HashSet<File>();

			scanned.addAll(gatewayFiles);
			scanned.addAll(studioFiles);

			/* early match for jars which may map to wrong artifacts */
			repo.match("com.axway.apigw.thirdparty.bcel", "BCEL", scanned);
			repo.match("com.axway.apigateway.thirdparty", "js-engine", scanned);
			repo.match("com.axway.passport", "axway-jaxb-protocol", scanned);
			repo.match("com.axway.passport", "axway-jaxb-log", scanned);
			repo.match("com.axway.passport", "axway-jaxb-audit", scanned);
			repo.match("com.axway.passport", "axway-util", scanned);
			repo.match("com.axway.passport", "passport-sdk", scanned);
			repo.match("com.axway.passport", "passport-api", scanned);
			repo.match("com.axway.passport", "passport-client-api", scanned);
			repo.match("com.ibm.icu", "com.springsource.com.ibm.icu", scanned);
			repo.match("com.sun.xml.bind", "jaxb1-impl", scanned);
			repo.match("net.sourceforge.saxon", "saxon", "dom", scanned);
			repo.match("net.sourceforge.saxon", "saxon", scanned);
			repo.match("com.wutka", "dtdparser", scanned);
			repo.match("org.python", "jython", scanned);
			repo.match("org.fusesource", "sigar", scanned);
			repo.match("javax.xml.soap", "saaj-api", scanned);
			repo.match("com.oracle", "ojdbc6", scanned);

			/* match filter base artifacts */
			repo.match("com.vordel.rcp", "com.vordel.rcp.filterbase", scanned);

			List<String> rcpVersions = repo.findVersions("com.vordel.rcp", "com.vordel.rcp.filterbase", "jar");
			Map<String, Set<File>> rcpFiles = new HashMap<String, Set<File>>();

			for(String version : rcpVersions) {
				Artifact artifact = new DefaultArtifact("com.vordel.rcp", "com.vordel.rcp.filterbase", "jar", version);
				File file = repo.findArtifact(artifact);

				if ((file != null) && file.isDirectory()) {
					Set<File> files = new HashSet<File>();
					File rcpLibs = new File(file, "lib");
					File vordelLib = new File(rcpLibs, "vordel.jar");

					MavenizerRepository.scan(rcpLibs, files);
					files.add(file);

					if (vordelLib.exists() && vordelLib.isFile()) {
						files.remove(vordelLib);

						repo.artifact("com.axway.apigw", "filter-devkit-studio-runtime", "jar", version, vordelLib);
					}

					scanned.addAll(files);


					rcpFiles.put(version, files);
				}
			}

			/* finish jar registration */
			repo.register(scanned);

			/* search maven central artifacts */
			repo.search(sys, "http://search.maven.org/solrsearch/");

			repo.match("org.eclipse.swt", "org.eclipse.swt.gtk.linux.x86_64", scanned);
			repo.match("org.eclipse.core", "org.eclipse.core.commands", scanned);
			repo.match("org.eclipse.jface", "org.eclipse.jface", scanned);
			repo.match("org.eclipse.jface", "org.eclipse.jface.text", scanned);

			Map<String, MetaPOM> matches = new HashMap<String, MetaPOM>();

			/* process maven descriptors for gateway and policy studio */
			processPOM(repo, gatewayFiles, matches, false);
			processPOM(repo, scanned, matches, false);

			repo.collect(matches, system, session);

			/* now process probable artifacts */
			processPOM(repo, scanned, matches, true);
			repo.collect(matches, system, session);

			repo.resolve(system, session);

			File gatewayDependencies = File.createTempFile("filter-deps-runtime", ".pom");

			gatewayDependencies.deleteOnExit();

			/* ensure all artifacts collected (even generated ones) */ 
			repo.collect(matches, system, session);

			Model gatewayDependenciesModel = writeServerDependenciesPOM(sys, session, repo, gatewayVersion, gatewayFiles);

			writePOM(gatewayDependencies, gatewayDependenciesModel);
			repo.install(system, session, gatewayDependenciesModel.getGroupId(), gatewayDependenciesModel.getArtifactId(), "pom", gatewayDependenciesModel.getVersion(), gatewayDependencies);

			for(Map.Entry<String, Set<File>> entry : rcpFiles.entrySet()) {
				File studioDependencies = File.createTempFile("filter-deps-studio", ".pom");
				Model studioDependenciesModel = writeStudioDependenciesPOM(sys, session, repo, entry.getKey(), entry.getValue());

				studioDependencies.deleteOnExit();

				writePOM(studioDependencies, studioDependenciesModel);
				repo.install(system, session, studioDependenciesModel.getGroupId(), studioDependenciesModel.getArtifactId(), "pom", studioDependenciesModel.getVersion(), studioDependencies);
			}

			Set<File> uniques = new HashSet<File>();

			repo.remaining(uniques);
			repo.unresolvable(uniques);
		} catch (IOException e) {
			usage("unexpected exception");
		} catch (SolrServerException e) {
			usage("unexpected exception");
		}
	}

	private static Model writeServerDependenciesPOM(GatewayRepoSys sys, RepositorySystemSession session, MavenizerRepository repo, String version, Set<File> scanned) throws IOException {
		InputStream is = GatewayFullMavenizer.class.getResourceAsStream("filter-deps-runtime.xml");
		Model target = MavenMetaMap.parseModel(MavenMetaMap.readFile(is));

		target.setVersion(version);
		repo.setDependencies(sys.getSystem(), session, target, scanned, true);

		return target;
	}

	private static Model writeStudioDependenciesPOM(GatewayRepoSys sys, RepositorySystemSession session, MavenizerRepository repo, String version, Set<File> scanned) throws IOException {
		InputStream is = GatewayFullMavenizer.class.getResourceAsStream("filter-deps-studio.xml");
		Model target = MavenMetaMap.parseModel(MavenMetaMap.readFile(is));

		target.setVersion(version);
		repo.setDependencies(sys.getSystem(), session, target, scanned, true);

		return target;
	}

	private static String getGatewayVersion(MavenizerRepository repo, Set<File> scanned) {
		repo.match("com.vordel.version", "vordel-version", scanned);

		Iterator<String> versions = repo.findVersions("com.vordel.version", "vordel-version", "jar").iterator();

		return versions.hasNext() ? versions.next() : null;
	}

	private static void processPOM(MavenizerRepository repo, Set<File> scanned, Map<String, MetaPOM> matches, boolean recursive) {
		MavenMetaMap metaPoms = new MavenMetaMap(scanned);

		for(MavenMetaMap.MetaPOM pom : metaPoms.values()) {
			Artifact artifact = new DefaultArtifact(pom.getGroupId(), pom.getArtifactId(), pom.getClassifier(), "jar", pom.getVersion());
			Model model = pom.getModel();

			if ((repo.match(artifact, scanned) != null) && (matches != null)) {
				matches.put(ArtifactIdUtils.toId(pom), pom);
			}

			if (recursive) {
				processModelDependencies(repo, model.getDependencyManagement(), scanned);
				processModelDependencies(repo, model.getDependencies(), scanned);
			}
		}
	}

	private static void processModelDependencies(MavenizerRepository repo, DependencyManagement management, Set<File> scanned) {
		if (management != null) {
			processModelDependencies(repo, management.getDependencies(), scanned);
		}
	}

	private static void processModelDependencies(MavenizerRepository repo, List<Dependency> dependencies, Set<File> scanned) {
		if (dependencies != null) {
			for(Dependency dependency : dependencies) {
				String groupId = dependency.getGroupId();
				String artifactId = dependency.getArtifactId();
				String classifier = dependency.getClassifier();

				if ((groupId.indexOf("${") == -1) && (artifactId.indexOf("${") == -1) && ((classifier == null) || (classifier.indexOf("${") == -1))) {
					String version = dependency.getVersion();

					if ((version != null) && (version.indexOf("${") != -1)) {
						version = null;
					}

					Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, "jar", version);

					repo.match(artifact, scanned);
				}

			}
		}
	}

	private static File createServerRuntime(File libs, Set<File> scanned) throws IOException {
		File server = File.createTempFile("server", ".jar");

		/* mark server file to be deleted on exit */
		server.deleteOnExit();

		System.out.println("Create server runtime archive");

		JarOutputStream out = new JarOutputStream(new FileOutputStream(server));

		try {
			addRuntimeArchive(out, new File(libs, "apiportal.jar"), scanned);
			addRuntimeArchive(out, new File(libs, "circuit.jar"), scanned);
			addRuntimeArchive(out, new File(libs, "client.jar"), scanned);
			addRuntimeArchive(out, new File(libs, "coercers.jar"), scanned);
			addRuntimeArchive(out, new File(libs, "common.jar"), scanned);
			addRuntimeArchive(out, new File(libs, "jwkjose.jar"), scanned);
			addRuntimeArchive(out, new File(libs, "logger.jar"), scanned);
			addRuntimeArchive(out, new File(libs, "manager.jar"), scanned);
			addRuntimeArchive(out, new File(libs, "oauthclient.jar"), scanned);
			addRuntimeArchive(out, new File(libs, "openidconnect.jar"), scanned);
			addRuntimeArchive(out, new File(libs, "precipitate.jar"), scanned);
			addRuntimeArchive(out, new File(libs, "security.jar"), scanned);
			addRuntimeArchive(out, new File(libs, "server.jar"), scanned);
			addRuntimeArchive(out, new File(libs, "testClient.jar"), scanned);
			addRuntimeArchive(out, new File(libs, "upgrade.jar"), scanned);
			addRuntimeArchive(out, new File(libs, "wspolicy.jar"), scanned);
			addRuntimeArchive(out, new File(libs, "xmlutils.jar"), scanned);
			addRuntimeArchive(out, new File(libs, "com.vordel.jms.jar"), scanned);

			for (File filter : libs.listFiles()) {
				String name = filter.getName();

				if (name.startsWith("com.vordel.circuit.") && (name.endsWith(".jar"))) {
					addRuntimeArchive(out, filter, scanned);
				}
			}
		} finally {
			out.close();
		}

		scanned.add(server);

		return server;
	}

	private static void addRuntimeArchive(JarOutputStream out, File file, Set<File> scanned) throws IOException {
		if (file.exists() && file.isFile()) {
			System.out.println(String.format("adding file '%s' to server runtime", file.getName()));

			Map<String, String> thumbprints = new HashMap<String, String>();
			JarFile jar = new JarFile(file);

			try {
				boolean fromMaven = false;

				/* check if we have maven meta data in the archive */
				for (Enumeration<? extends JarEntry> iterator = jar.entries(); iterator.hasMoreElements();) {
					JarEntry item = iterator.nextElement();
					String name = item.getName();

					fromMaven |= name.startsWith("META-INF/maven");
				}

				if (!fromMaven) {
					scanned.remove(file);

					for (Enumeration<? extends JarEntry> iterator = jar.entries(); iterator.hasMoreElements();) {
						JarEntry item = iterator.nextElement();
						String name = item.getName();
						boolean write = true;

						write &= !name.endsWith("/");
						write &= !"META-INF/MANIFEST.MF".equalsIgnoreCase(name);

						if (write) {
							byte[] data = MavenMetaMap.extractFile(jar, item);
							String sha1 = MavenizerRepository.sha1(data);

							write &= !sha1.equals(thumbprints.get(name));

							if (write) {
								out.putNextEntry(item);
								out.write(data);
								out.closeEntry();
							}
						}
					}
				}
			} finally {
				jar.close();
			}
		}
	}

	public static void writePOM(File file, Model model) throws IOException {
		MavenXpp3Writer writer = new MavenXpp3Writer();

		mkdir(file.getParentFile());

		FileOutputStream out = new FileOutputStream(file);

		try {
			writer.write(out, model);
		} finally {
			out.close();
		}
	}

	private static void mkdir(File directory) {
		if (!directory.exists()) {
			mkdir(directory.getParentFile());

			directory.mkdir();
		}
	}
}
