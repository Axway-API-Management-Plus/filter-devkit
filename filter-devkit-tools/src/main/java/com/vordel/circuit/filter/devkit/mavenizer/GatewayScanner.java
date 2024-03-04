package com.vordel.circuit.filter.devkit.mavenizer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;

import com.google.common.io.Files;
import com.vordel.circuit.filter.devkit.mavenizer.dist.AetherUtils;
import com.vordel.circuit.filter.devkit.mavenizer.dist.Mavenizer;
import com.vordel.circuit.filter.devkit.mavenizer.maps.MavenMetaMap;

public class GatewayScanner {
	public static void usage(String format, Object... args) {
		System.err.println(String.format(format, args));
		System.err.println("GatewayScanner <vdistdir> [pom with artifact items]");
		System.exit(-1);
	}

	private static File scanGateway(File root, Set<File> files, Map<File, File> duplicates) {
		if ((!root.exists()) || (!root.isDirectory())) {
			usage("'%s' is not a directory or does not exist", root);
		}

		File libs = new File(new File(root, "system"), "lib");

		if ((!libs.exists()) || (!libs.isDirectory())) {
			usage("'%s' is not a directory or does not exist", libs);
		}

		AetherUtils.scan(libs, files, duplicates);

		return libs;
	}

	public static void main(String[] args) throws IOException {
		Map<File, File> duplicates = new HashMap<File, File>();
		Set<File> scanned = new HashSet<File>();
		File vdist = null;
		File items = null;

		File libs = null;

		if (args.length == 1) {
			vdist = new File(args[0]);
			libs = scanGateway(vdist, scanned, duplicates);
		} else if (args.length == 2) {
			vdist = new File(args[0]);
			libs = scanGateway(vdist, scanned, duplicates);

			items = new File(args[1]);
		} else {
			usage("Please provide root directory");
		}

		if (libs.exists()) {
			Mavenizer mavenizer = new Mavenizer.Builder().build();
			RepositorySystemSession session = mavenizer.getSession();

			MavenResult result = new MavenResult(scanned, duplicates);

			/* start by checking jars from inlined metadata */
			result.addMetaDependencies(session);

			result.addProbableArtifact("com.amazonaws", "aws-java-sdk");
			result.addProbableArtifact("org.ow2.asm", "asm-all");

			/* collect dependency graph (first time for probable dependencies) */
			result.collectDependencies(mavenizer, session);

			if (items != null) {
				/* match artifacts from provided pom (if any) */
				result.addPOMArtifactItems(items);

				/* collect dependency graph again (from matched pom artifacts) */
				result.collectDependencies(mavenizer, session);
			}

			/* at this point, assume that probable dependencies are real dependencies */
			result.mergeProbableDependencies();

			/* collect merged dependencies (also recollect all managed dependencies) */
			result.collectDependencies(mavenizer, session, true);

			/* try to resolve any dependency which does not have a dependency graph */
			result.resolveDependencies(mavenizer, session);
			String version = getGatewayVersion(result);

			if (version != null) {
				File gatewayPublicDependencies = File.createTempFile("filter-central-runtime", ".pom");
				File target = new File("build/generated");
				File out = new File(target, "apigw-deps-runtime");

				gatewayPublicDependencies.deleteOnExit();

				Map<File, String> dist = copyDistFiles(mavenizer, session, result, version, duplicates, vdist, out);

				Model central = writeServerPublicDependenciesPOM(mavenizer, session, result, version);
				writePOM(new File(target, "filter-central-runtime.xml"), central);
				
				
				String gradleDependencies = result.printGradleDependencies(dist);
				File gradle = new File(target, "apigateway.gradle");
				
				writeGradleFile(gradle, version, gradleDependencies);

			}
		}
	}

	private static Map<File, String> copyDistFiles(Mavenizer mavenizer, RepositorySystemSession session, MavenResult result, String version, Map<File, File> duplicates, File vdist, File out) throws IOException {
		Map<File, String> libs = new HashMap<File, String>();
		File dist = new File(out, "dist");
		URI root = vdist.toURI();

		for(File jar : result.getScanned()) {
			String path = null;

			if (jar.exists()) {
				URI uri = jar.toURI();

				if (uri.getPath().startsWith(root.getPath())) {
					uri = root.relativize(uri);
					path = uri.getPath();

					libs.put(jar, path);
				}
			}

			if ((path != null) && jar.isFile() && (!duplicates.containsKey(jar))) {
				boolean copy = (result.getArtifact(jar) == null) || (!result.isResolved(jar));

				if (copy) {
					Path relative = vdist.toPath().relativize(jar.toPath());
					File target = dist.toPath().resolve(relative).toFile();

					target.getParentFile().mkdirs();
					Files.copy(jar, target);
				}
			}
		}

		Model gatewayDependenciesModel = writeServerSystemDependenciesPOM(mavenizer, session, result, version, libs);

		File pom = new File(out, "pom.xml");

		writePOM(pom, gatewayDependenciesModel);
		
		return libs;
	}

	private static void writeGradleFile(File file, String version, String dependencies) throws IOException {
		String template = readTemplate(GatewayScanner.class.getResourceAsStream("apigateway.gradle"));
		
		template = template.replace("<apigwVersion>", version);
		template = template.replace("<apigwCentralDependencies>", dependencies);
		
		file.getParentFile().mkdirs();

		FileOutputStream out = new FileOutputStream(file);

		try {
			Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
			
			try {
				writer.append(template);
			} finally {
				writer.close();
			}
		} finally {
			out.close();
		}
	}

	private static final String readTemplate(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int count = 0;

		while ((count = in.read(buffer)) > -1) {
			out.write(buffer, 0, count);
		}

		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}

	private static Model writeServerSystemDependenciesPOM(Mavenizer mavenizer, RepositorySystemSession session, MavenResult result, String version, Map<File, String> libs) throws IOException {
		InputStream is = GatewayScanner.class.getResourceAsStream("apigw-deps-runtime.xml");
		Model target = MavenMetaMap.parseModel(MavenMetaMap.readFile(is));

		target.setVersion(version);
		result.setModelDependencies(mavenizer, session, target, libs, true);

		return target;
	}

	private static Model writeServerPublicDependenciesPOM(Mavenizer mavenizer, RepositorySystemSession session, MavenResult result, String version) throws IOException {
		InputStream is = GatewayScanner.class.getResourceAsStream("filter-central-runtime.xml");
		Model target = MavenMetaMap.parseModel(MavenMetaMap.readFile(is));

		target.setVersion(version);
		result.setModelDependencies(mavenizer, session, target, null, false);

		return target;
	}

	private static String getGatewayVersion(MavenResult result) {
		Artifact version = result.find("com.vordel.version", "vordel-version", null);

		if (version != null) {
			return version.getVersion();
		} else {
			System.err.println("Unable to find version artifact");
		}

		return null;
	}

	public static void writePOM(File file, Model model) throws IOException {
		MavenXpp3Writer writer = new MavenXpp3Writer();

		file.getParentFile().mkdirs();

		FileOutputStream out = new FileOutputStream(file);

		try {
			writer.write(out, model);
		} finally {
			out.close();
		}
	}
}
