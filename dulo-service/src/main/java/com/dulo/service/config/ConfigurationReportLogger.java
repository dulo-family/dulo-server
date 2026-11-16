package com.dulo.service.config;

import com.dulo.core.setup.port.in.GetSetupStatusUseCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/**
 * Logs the merged configuration on start while the initial setup is pending, or always when {@code
 * dulo.print-config} is true.
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

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {
        final boolean setupPending = this.setupStatus.isSetupPending();
        if (setupPending || properties.printConfig()) {
            LOG.info(
                    "{}Merged configuration:\n{}",
                    setupPending ? "Initial setup pending. " : "",
                    report.render(properties));
        }
    }
}
