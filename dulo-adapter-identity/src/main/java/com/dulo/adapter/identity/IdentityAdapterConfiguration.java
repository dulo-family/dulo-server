package com.dulo.adapter.identity;

import com.dulo.adapter.identity.api.ApiSecurityConfiguration;
import com.dulo.adapter.identity.authserver.AuthorizationServerConfiguration;
import com.dulo.adapter.identity.keys.SigningKeyConfiguration;
import com.dulo.adapter.identity.signin.SignInConfiguration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * Entry point of the identity adapter. Import it, together with an {@link
 * com.dulo.adapter.identity.settings.IdentityAdapterSettings} bean, to secure the server.
 *
 * <p>Dulo plays three OAuth roles: a client of the upstream identity providers that users sign in
 * with, an authorization server that issues Dulo's own tokens, and a resource server that checks
 * those tokens on every API call.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@Import({
    ApiSecurityConfiguration.class,
    AuthorizationServerConfiguration.class,
    SignInConfiguration.class,
    SigningKeyConfiguration.class
})
public class IdentityAdapterConfiguration {}
