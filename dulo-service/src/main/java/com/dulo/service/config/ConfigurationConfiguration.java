package com.dulo.service.config;

import com.dulo.core.identity.port.out.IdentityProvidersPort;
import com.dulo.core.setup.port.in.GetSetupStatusUseCase;
import com.dulo.core.setup.port.out.ConfigurationReportPort;
import com.dulo.core.tree.port.out.FeatureOverridesPort;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

import java.security.SecureRandom;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DuloProperties.class)
public class ConfigurationConfiguration {

    @Bean
    FeatureOverridesPort featureOverridesPort(final DuloProperties properties) {
        final var overrides = properties.featureOverrides();
        return () -> overrides;
    }

    @Bean
    IdentityProvidersPort identityProvidersPort(final DuloProperties properties) {
        return new ConfiguredIdentityProviders(properties);
    }

    @Bean
    ConfigurationReport configurationReport(final ConfigurableEnvironment environment) {
        return new ConfigurationReport(environment);
    }

    /** The configuration report the setup shows, the same one the log gets. */
    @Bean
    ConfigurationReportPort configurationReportPort(
            final ConfigurationReport report, final DuloProperties properties) {
        return () -> report.render(properties);
    }

    @Bean
    SetupToken setupToken(final DuloProperties properties) {
        return new SetupToken(properties.setup().token(), new SecureRandom());
    }

    @Bean
    ConfigurationReportLogger configurationReportLogger(
            final ConfigurationReport report,
            final DuloProperties properties,
            final GetSetupStatusUseCase setupStatus,
            final SetupToken setupToken) {
        return new ConfigurationReportLogger(report, properties, setupStatus, setupToken);
    }
}
