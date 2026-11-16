package com.dulo.adapter.rest.setup;

import io.swagger.v3.oas.annotations.media.Schema;

record SetupStatusResponse(
        @Schema(
                        description =
                                "True until the initial setup is done. Until then the rest of the"
                                        + " API answers 503.")
                boolean pending) {}
