package com.lunacode.tui;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 当前 LunaCode 构建的版本信息。
 */
public record BuildInfo(String version) {
    private static final String DEVELOPMENT_VERSION = "dev";
    private static final String POM_PROPERTIES =
            "META-INF/maven/com.lunacode/lunacode/pom.properties";

    public BuildInfo {
        version = normalized(version);
    }

    /**
     * 按包版本、Maven 属性、开发回退的顺序读取构建版本。
     */
    public static BuildInfo load() {
        String implementationVersion = BuildInfo.class.getPackage().getImplementationVersion();
        return resolve(implementationVersion, loadMavenProperties());
    }

    static BuildInfo resolve(String implementationVersion, Properties mavenProperties) {
        if (!isBlank(implementationVersion)) {
            return new BuildInfo(implementationVersion.trim());
        }
        String mavenVersion = mavenProperties == null ? null : mavenProperties.getProperty("version");
        return new BuildInfo(mavenVersion);
    }

    private static Properties loadMavenProperties() {
        ClassLoader classLoader = BuildInfo.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(POM_PROPERTIES)) {
            if (input == null) {
                return new Properties();
            }
            Properties properties = new Properties();
            properties.load(input);
            return properties;
        } catch (IOException ignored) {
            return new Properties();
        }
    }

    private static String normalized(String value) {
        return isBlank(value) ? DEVELOPMENT_VERSION : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
