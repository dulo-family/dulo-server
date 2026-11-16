package com.dulo.service.config;

import com.dulo.core.setup.port.in.GetSetupStatusUseCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Logs the merged configuration on start while the initial setup is pending, or always when {@code
 * dulo.print-config} is true. Also warns about settings that leave nobody able to sign in, which
 * usually means the configuration file wasn't found.
 */
class ConfigurationReportLogger implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationReportLogger.class);

    private final ConfigurationReport report;
    private final DuloProperties properties;
    private final GetSetupStatusUseCase setupStatus;

    ConfigurationReportLogger(
            final ConfigurationReport report,
            final DuloProperties properties,
            final GetSetupStatusUseCase setupStatus) {
        this.report = report;
        this.properties = properties;
        this.setupStatus = setupStatus;
    }

    /** Settings that leave nobody able to sign in, as warnings to log. */
    static List<String> signInWarnings(final DuloProperties properties) {
        final List<String> warnings = new ArrayList<>();
        if (properties.identity().providers().isEmpty()) {
            warnings.add(
                    "No identity providers are configured (dulo.identity.providers), so nobody"
                            + " can sign in.");
        }
        if (properties.clients().web().redirectUris().isEmpty()) {
            warnings.add(
                    "The web client has no redirect URIs (dulo.clients.web.redirect-uris), so the"
                            + " authorization server has no clients and rejects every sign-in.");
        }
        if (!warnings.isEmpty()) {
            warnings.add(
                    "The configuration file is /config/dulo.yaml, or the file DULO_CONFIG_FILE"
                            + " names. Check that it exists and is readable.");
        }
        return warnings;
    }

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {
        signInWarnings(this.properties).forEach(LOG::warn);
        final boolean setupPending = this.setupStatus.isSetupPending();
        if (setupPending || this.properties.printConfig()) {
            LOG.info(
                    "{}Merged configuration:\n{}",
                    setupPending ? "Initial setup pending. " : "",
                    this.report.render(this.properties));
        }
    }
}
