package com.dulo.core.setup.port.out;

/** The server's merged configuration as text, with secrets masked. */
public interface ConfigurationReportPort {

    String report();
}
