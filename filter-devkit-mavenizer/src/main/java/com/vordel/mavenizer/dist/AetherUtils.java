package com.vordel.mavenizer.dist;

import java.io.File;

import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;

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
}
