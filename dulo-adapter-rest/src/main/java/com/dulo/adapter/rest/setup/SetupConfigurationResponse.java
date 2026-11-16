package com.dulo.adapter.rest.setup;

import io.swagger.v3.oas.annotations.media.Schema;

record SetupConfigurationResponse(
        @Schema(
                        description =
                                "The merged configuration as YAML, with secrets masked and the"
                                        + " source of each value as a comment.")
                String configuration) {}
