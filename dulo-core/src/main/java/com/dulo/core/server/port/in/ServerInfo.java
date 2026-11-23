package com.dulo.core.server.port.in;

/**
 * What a client may know about the server.
 *
 * @param version the server's version, e.g. {@code 0.4.0}
 */
public record ServerInfo(String version) {}
