package com.vordel.mavenizer.dist;

import java.io.File;
import java.net.MalformedURLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.classpath.ClasspathTransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.ConservativeAuthenticationSelector;
import org.eclipse.aether.util.repository.DefaultAuthenticationSelector;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.DefaultProxySelector;

public class GatewayRepoSys {
	private static final ModelBuilder MODEL_BUILDER = new DefaultModelBuilderFactory().newInstance();

	private static final SettingsBuilder SETTINGS_BUILDER = new DefaultSettingsBuilderFactory().newInstance();

	private final DefaultServiceLocator locator;

	private final DefaultPlexusContainer container;

	private final SettingsDecrypter decrypter;

	private RepositorySystem repoSys = null;

	//private RemoteRepositoryManager remoteRepoMan = null;

	private Settings settings = null;

	private File userSettings;

	private File globalSettings;

	private final Properties userProperties = new Properties();
	private final Properties systemProperties = new Properties();

	private final WorkspaceReader workspace;
	
	public static RemoteRepository CENTRAL_REPOSITORY = getCentralRepository();

	private static <T> boolean eq(T o1, T o2) {
		return (o1 == null) ? o2 == null : o1.equals(o2);
	}

	private static RemoteRepository getCentralRepository() {
		RemoteRepository.Builder builder = new RemoteRepository.Builder("central", "default", "http://repo.maven.apache.org/maven2");
		
		return builder.build();
	}

	public GatewayRepoSys() {
		this(null, null);
	}

	public GatewayRepoSys(WorkspaceReader workspace) {
		this(null, workspace);
	}

	public GatewayRepoSys(ClassRealm realm, WorkspaceReader workspace) {
		ClassWorld classWorld = new ClassWorld("plexus.core", Thread.currentThread().getContextClassLoader());
		ContainerConfiguration cc = new DefaultContainerConfiguration().setClassWorld(classWorld).setRealm(realm).setClassPathScanning(PlexusConstants.SCANNING_INDEX).setAutoWiring(true).setName("maven");

		try {
			container = new DefaultPlexusContainer(cc);
			decrypter = container.lookup(SettingsDecrypter.class);

			locator = MavenRepositorySystemUtils.newServiceLocator();
			locator.setServices(ModelBuilder.class, MODEL_BUILDER);
			locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
			locator.addService(TransporterFactory.class, FileTransporterFactory.class);
			locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
			locator.addService(TransporterFactory.class, ClasspathTransporterFactory.class);
		} catch (PlexusContainerException e) {
			throw new IllegalStateException(e);
		} catch (ComponentLookupException e) {
			throw new IllegalStateException(e);
		}
		
		this.workspace = workspace;
		this.systemProperties.putAll(System.getProperties());
	}

	public SolrServer getSolrServer(String url) {
		try {
			CommonsHttpSolrServer server = new CommonsHttpSolrServer(url);
			Proxy proxy = getSettings().getActiveProxy();

			HttpClient client = server.getHttpClient();
			HostConfiguration params = client.getHostConfiguration();

			if (proxy != null) {
				String user = proxy.getUsername();
				String password = proxy.getPassword();

				params.setProxy(proxy.getHost(), proxy.getPort());

				if (user != null) {
					Credentials creds = new UsernamePasswordCredentials(user, password);

					client.getState().setProxyCredentials(AuthScope.ANY, creds);
				}
			}

			server.setParser(new XMLResponseParser());

			return server;
		} catch (MalformedURLException e) {
			throw new IllegalStateException(e);
		}
	}

	public synchronized RepositorySystem getSystem() {
		if (repoSys == null) {
			repoSys = locator.getService(RepositorySystem.class);
			if (repoSys == null) {
				throw new IllegalStateException("The repository system could not be initialized");
			}
		}
		return repoSys;
	}

//	private synchronized RemoteRepositoryManager getRemoteRepoMan() {
//		if (remoteRepoMan == null) {
//			remoteRepoMan = locator.getService(RemoteRepositoryManager.class);
//			if (remoteRepoMan == null) {
//				throw new IllegalStateException("The repository system could not be initialized");
//			}
//		}
//		return remoteRepoMan;
//	}

	public RepositorySystemSession getSession() {
		return getSession(false);
	}
	
	public RepositorySystemSession getOfflineSession() {
		return getSession(true);
	}
	
	private RepositorySystemSession getSession(boolean offline) {
		DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
		Map<Object, Object> configProps = new LinkedHashMap<Object, Object>();

		session.setConfigProperties(configProps);
		session.setOffline(offline);
		session.setUserProperties(getUserProperties());
		session.setProxySelector(getProxySelector());
		session.setMirrorSelector(getMirrorSelector());
		session.setAuthenticationSelector(getAuthSelector());

		session.setCache(new DefaultRepositoryCache());
		session.setLocalRepositoryManager(getLocalRepoMan(session));
		session.setWorkspaceReader(workspace);

		return session;
	}

	private File getDefaultLocalRepoDir() {
		Settings settings = getSettings();

		if (settings.getLocalRepository() != null) {
			return new File(settings.getLocalRepository());
		}

		return new File(SettingsXmlConfigurationProcessor.USER_MAVEN_CONFIGURATION_HOME, "repository");
	}

	private LocalRepositoryManager getLocalRepoMan(RepositorySystemSession session) {
		org.eclipse.aether.repository.LocalRepository repo = new org.eclipse.aether.repository.LocalRepository(getDefaultLocalRepoDir());

		return getSystem().newLocalRepositoryManager(session, repo);
	}

	private synchronized Settings getSettings() {
		if (settings == null) {
			DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
			request.setUserSettingsFile(getUserSettings());
			request.setGlobalSettingsFile(getGlobalSettings());
			request.setSystemProperties(getSystemProperties());
			request.setUserProperties(getUserProperties());

			try {
				settings = SETTINGS_BUILDER.build(request).getEffectiveSettings();
			} catch (SettingsBuildingException e) {
				throw new IllegalStateException(e);
			}

			SettingsDecryptionResult result = decrypter.decrypt(new DefaultSettingsDecryptionRequest(settings));

			settings.setServers(result.getServers());
			settings.setProxies(result.getProxies());
		}

		return settings;
	}

	private ProxySelector getProxySelector() {
		DefaultProxySelector selector = new DefaultProxySelector();

		Settings settings = getSettings();
		for (Proxy proxy : settings.getProxies()) {
			AuthenticationBuilder auth = new AuthenticationBuilder();
			auth.addUsername(proxy.getUsername()).addPassword(proxy.getPassword());
			selector.add(new org.eclipse.aether.repository.Proxy(proxy.getProtocol(), proxy.getHost(), proxy.getPort(), auth.build()), proxy.getNonProxyHosts());
		}

		return selector;
	}

	private MirrorSelector getMirrorSelector() {
		DefaultMirrorSelector selector = new DefaultMirrorSelector();

		Settings settings = getSettings();
		for (Mirror mirror : settings.getMirrors()) {
			selector.add(String.valueOf(mirror.getId()), mirror.getUrl(), mirror.getLayout(), false, mirror.getMirrorOf(), mirror.getMirrorOfLayouts());
		}

		return selector;
	}

	private AuthenticationSelector getAuthSelector() {
		DefaultAuthenticationSelector selector = new DefaultAuthenticationSelector();

		Settings settings = getSettings();
		for (Server server : settings.getServers()) {
			AuthenticationBuilder auth = new AuthenticationBuilder();
			auth.addUsername(server.getUsername()).addPassword(server.getPassword());
			auth.addPrivateKey(server.getPrivateKey(), server.getPassphrase());
			selector.add(server.getId(), auth.build());
		}

		return new ConservativeAuthenticationSelector(selector);
	}

	public Properties getUserProperties() {
		return userProperties;
	}

	private Properties getSystemProperties() {
		return systemProperties;
	}

	public synchronized void setUserSettings(File file) {
		if (!eq(this.userSettings, file)) {
			settings = null;
		}
		this.userSettings = file;
	}

	public File getUserSettings() {
		if (userSettings == null) {
			userSettings = AetherUtils.findUserSettings();
		}
		return userSettings;
	}

	public void setGlobalSettings(File file) {
		if (!eq(this.globalSettings, file)) {
			settings = null;
		}
		this.globalSettings = file;
	}

	public File getGlobalSettings() {
		if (globalSettings == null) {
			globalSettings = AetherUtils.findGlobalSettings();
		}
		return globalSettings;
	}
}
