package com.dulo.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.Map;

class ConfigurationReportTest {

    private static String report(final Map<String, Object> env, final Map<String, Object> file) {
        final StandardEnvironment environment = new StandardEnvironment();
        final var sources = environment.getPropertySources();
        sources.replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env));
        sources.addLast(
                new MapPropertySource(
                        "Config resource 'file [/config/dulo.yaml]' via location 'optional:file:/config/dulo.yaml'",
                        file));
        // The built-in defaults, as application.yaml provides them.
        sources.addLast(
                new MapPropertySource(
                        "Config resource 'class path resource [application.yaml]' via location"
                                + " 'optional:classpath:/'",
                        Map.of("dulo.database.url", "jdbc:sqlite:data/dulo.db")));
        final DuloProperties properties =
                Binder.get(environment).bind("dulo", DuloProperties.class).get();
        return new ConfigurationReport(environment).render(properties);
    }

    @Test
    void namesEverySourceOfAValueLowestPriorityFirst() {
        final String report =
                report(
                        Map.of(
                                "DULO_PUBLIC_URL", "https://tree.example.org",
                                "DULO_CLIENTS_WEB_REDIRECTURIS_0", "https://a.example.org/cb",
                                "DULO_CLIENTS_WEB_REDIRECTURIS_1", "https://b.example.org/cb"),
                        Map.of(
                                "dulo.public-url", "https://file.example.org",
                                "dulo.database.url", "jdbc:postgresql://db:5432/dulo"));

        assertThat(report)
                .contains("# Database: POSTGRESQL")
                .contains(
                        "public-url: https://tree.example.org  # default → file /config/dulo.yaml"
                                + " → environment variable DULO_PUBLIC_URL")
                .contains(
                        "redirect-uris: [https://a.example.org/cb, https://b.example.org/cb]  #"
                                + " default → environment variables"
                                + " DULO_CLIENTS_WEB_REDIRECTURIS_0,"
                                + " DULO_CLIENTS_WEB_REDIRECTURIS_1")
                .contains("url: jdbc:postgresql://db:5432/dulo  # default → file /config/dulo.yaml")
                .contains("print-config: false  # default");
    }

    @Test
    void startsWithTheDefaultOnlyWhenDuloHasOne() {
        final String report =
                report(
                        Map.of(
                                "DULO_IDENTITY_PROVIDERS_GOOGLE_TYPE", "google",
                                "DULO_IDENTITY_PROVIDERS_GOOGLE_CLIENTID", "abc"),
                        Map.of("dulo.identity.providers.google.claims.roles", "roles"));

        assertThat(report)
                .contains("client-id: abc  # environment variable")
                .contains("roles: roles  # default → file /config/dulo.yaml")
                .contains("url: jdbc:sqlite:data/dulo.db  # default\n")
                .contains("email: email  # default\n");
    }

    @Test
    void masksSecretsAsValidYaml() {
        final String report =
                report(
                        Map.of(
                                "DULO_DATABASE_PASSWORD", "s3cret",
                                "DULO_IDENTITY_PROVIDERS_GOOGLE_TYPE", "google",
                                "DULO_IDENTITY_PROVIDERS_GOOGLE_CLIENTID", "abc",
                                "DULO_IDENTITY_PROVIDERS_GOOGLE_CLIENTSECRET", "google-secret"),
                        Map.of());

        assertThat(report)
                .contains("password: \"****\"  # environment variable DULO_DATABASE_PASSWORD")
                .doesNotContain("s3cret")
                .doesNotContain("google-secret");
        final JsonNode yaml = new YAMLMapper().readTree(report);
        assertThat(yaml.at("/dulo/database/password").asString()).isEqualTo("****");
        assertThat(yaml.at("/dulo/identity/providers/google/client-secret").asString())
                .isEqualTo("****");
    }

    @Test
    void convertsComponentNamesToKebabCase() {
        assertThat(ConfigurationReport.kebab("clientSecret")).isEqualTo("client-secret");
        assertThat(ConfigurationReport.kebab("dataDir")).isEqualTo("data-dir");
        assertThat(ConfigurationReport.kebab("url")).isEqualTo("url");
    }
}
