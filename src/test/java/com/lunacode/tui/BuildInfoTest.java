package com.lunacode.tui;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildInfoTest {
    @Test
    void implementationVersionHasPriority() {
        Properties properties = new Properties();
        properties.setProperty("version", "2.0.0");

        BuildInfo buildInfo = BuildInfo.resolve(" 1.4.0 ", properties);

        assertEquals("1.4.0", buildInfo.version());
    }

    @Test
    void mavenPropertiesAreUsedAsSecondSource() {
        Properties properties = new Properties();
        properties.setProperty("version", " 0.1.0-SNAPSHOT ");

        BuildInfo buildInfo = BuildInfo.resolve(null, properties);

        assertEquals("0.1.0-SNAPSHOT", buildInfo.version());
    }

    @Test
    void missingSourcesUseDevelopmentMarker() {
        assertEquals("dev", BuildInfo.resolve(null, new Properties()).version());
        assertEquals("dev", BuildInfo.resolve(" ", null).version());
        assertEquals("dev", new BuildInfo(null).version());
    }
}
