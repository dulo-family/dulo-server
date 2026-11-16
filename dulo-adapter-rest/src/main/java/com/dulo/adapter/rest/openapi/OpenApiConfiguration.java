package com.dulo.adapter.rest.openapi;

import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariable;
import io.swagger.v3.oas.models.servers.ServerVariables;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Header and schema rules of the generated OpenAPI document. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    /**
     * Version of the API contract, matching the {@code /api/v1} path. It is not the server version,
     * so the published OpenAPI document only changes when the API does.
     */
    static final String API_VERSION = "1";

    /**
     * Describes the API. The server URL is a variable, so clients and the Swagger UI can point it
     * at any Dulo server.
     *
     * @return the OpenAPI document header
     */
    @Bean
    OpenAPI duloOpenApi() {
        final ServerVariable baseUrl =
                new ServerVariable()
                        ._default("http://localhost:8080")
                        .description("Address of your Dulo server");
        final SecurityScheme oauth2 =
                new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .description(
                                "Dulo's own access tokens. Clients use the authorization code"
                                        + " flow with PKCE; users sign in through one of the"
                                        + " identity providers listed at"
                                        + " /api/v1/identity-providers.")
                        .flows(
                                new OAuthFlows()
                                        .authorizationCode(
                                                new OAuthFlow()
                                                        .authorizationUrl("/oauth2/authorize")
                                                        .tokenUrl("/oauth2/token")
                                                        .refreshUrl("/oauth2/token")
                                                        .scopes(
                                                                new Scopes()
                                                                        .addString(
                                                                                "openid",
                                                                                "Sign in"))));
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(SecuritySchemes.OAUTH2, oauth2))
                .info(
                        new Info()
                                .title("Dulo API")
                                .version(API_VERSION)
                                .description("REST API of the Dulo family tree server."))
                .servers(
                        List.of(
                                new Server()
                                        .url("{baseUrl}")
                                        .description("Dulo server")
                                        .variables(
                                                new ServerVariables()
                                                        .addServerVariable("baseUrl", baseUrl))));
    }

    /**
     * Marks record components as required unless they are {@code @Nullable}.
     *
     * @return the model converter
     */
    @Bean
    ModelConverter requiredRecordComponents() {
        return new RequiredRecordComponentsConverter();
    }
}
