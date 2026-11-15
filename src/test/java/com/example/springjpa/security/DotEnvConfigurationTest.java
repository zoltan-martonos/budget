package com.example.springjpa.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DotEnvConfigurationTest {

    @TempDir
    Path tempDir;

    @Test
    void importsDotEnvPropertiesWithSpringConfigImport() throws Exception {
        Path applicationProperties = tempDir.resolve("application.properties");
        Path dotenv = tempDir.resolve(".env");
        Files.writeString(applicationProperties,
                "spring.config.import=optional:file:" + dotenv.toUri().getPath() + "[.properties]\n");
        Files.writeString(dotenv, "budget.auth.legacy-owner-email=authorized@example.test\n");

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfiguration.class)
                .web(WebApplicationType.NONE)
                .run("--spring.config.location=" + applicationProperties.toUri())) {
            assertEquals("authorized@example.test",
                    context.getEnvironment().getProperty("budget.auth.legacy-owner-email"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {
    }
}
