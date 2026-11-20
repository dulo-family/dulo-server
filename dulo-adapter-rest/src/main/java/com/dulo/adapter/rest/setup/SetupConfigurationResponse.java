package com.dulo.adapter.rest.setup;

import io.swagger.v3.oas.annotations.media.Schema;

record SetupConfigurationResponse(
        @Schema(
                        description =
                                "The merged configuration as YAML, with secrets masked. Each value's"
                                        + " comment lists every source that sets it, lowest"
                                        + " priority first and separated by ` → `: the last one"
                                        + " wins. `default` comes first when Dulo has a default.")
                String configuration) {}
