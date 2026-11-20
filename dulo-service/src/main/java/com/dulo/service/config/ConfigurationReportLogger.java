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
 * dulo.print-config} is true. While the setup is pending it also says where to find the setup
 * token. Also warns about settings that leave nobody able to sign in, which usually means the
 * configuration file wasn't found.
 */
class ConfigurationReportLogger implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationReportLogger.class);

    private final ConfigurationReport report;
    private final DuloProperties properties;
    private final GetSetupStatusUseCase setupStatus;
    private final SetupToken setupToken;

    ConfigurationReportLogger(
            final ConfigurationReport report,
            final DuloProperties properties,
            final GetSetupStatusUseCase setupStatus,
            final SetupToken setupToken) {
        this.report = report;
        this.properties = properties;
        this.setupStatus = setupStatus;
        this.setupToken = setupToken;
    }

    /**
     * Where the person doing the setup finds the token. A generated token is logged, since the log
     * is the only place it exists; a configured one isn't.
     */
    static String setupMessage(final SetupToken setupToken) {
        return setupToken.generated()
                ? "Initial setup pending. Sign in through the web client and complete the setup"
                        + " with this one-time setup token: "
                        + setupToken.token()
                : "Initial setup pending. Sign in through the web client and complete the setup"
                        + " with the setup token from the configuration (dulo.setup.token).";
    }

    /** Settings that leave nobody able to sign in, as warnings to log. */
    static List<String> signInWarnings(final DuloProperties properties) {
        final List<String> warnings = new ArrayList<>();
        if (properties.identity().providers().isEmpty()) {
            warnings.add(
                    "No identity providers are configured (dulo.identity.providers), so nobody"
                            + " can sign in.");
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
            LOG.info("Merged configuration:\n{}", this.report.render(this.properties));
        }
        if (setupPending) {
            LOG.info(setupMessage(this.setupToken));
        }
    }
}
