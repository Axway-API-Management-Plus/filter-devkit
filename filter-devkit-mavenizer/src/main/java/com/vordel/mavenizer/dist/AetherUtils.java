package com.vordel.mavenizer.dist;

import java.io.File;
import java.util.List;
import java.util.ListIterator;

import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;

public class AetherUtils {
	public static File findGlobalSettings() {
		return SettingsXmlConfigurationProcessor.DEFAULT_GLOBAL_SETTINGS_FILE;
	}

	public static String getMavenHome() {
		return SettingsXmlConfigurationProcessor.USER_MAVEN_CONFIGURATION_HOME.getAbsolutePath();
	}

	public static File findUserSettings() {
		return SettingsXmlConfigurationProcessor.DEFAULT_USER_SETTINGS_FILE;
	}

	public static void setRemoteRepositoriesProxy(RepositorySystemSession session, List<RemoteRepository> repositories) {
		ListIterator<RemoteRepository> iterator = repositories.listIterator();

		while(iterator.hasNext()) {
			RemoteRepository repository = iterator.next();
			Proxy proxy = session.getProxySelector().getProxy(repository);

			if (proxy != null) {
				/* XXX help proxy selector (does not seems to work out of the box) */
				repository = new RemoteRepository.Builder(repository).setProxy(proxy).build();

				iterator.set(repository);
			}
		}
	}
}
