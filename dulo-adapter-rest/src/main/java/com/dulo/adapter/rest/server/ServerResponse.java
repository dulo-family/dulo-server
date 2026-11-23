package com.dulo.adapter.rest.server;

import com.dulo.core.server.port.in.ServerInfo;

import io.swagger.v3.oas.annotations.media.Schema;

record ServerResponse(
        @Schema(
                        description =
                                "The server's version, e.g. `0.4.0`, or `unknown` when it was built"
                                        + " without build information.",
                        example = "0.4.0")
                String version) {

    static ServerResponse from(final ServerInfo info) {
        return new ServerResponse(info.version());
    }
}
