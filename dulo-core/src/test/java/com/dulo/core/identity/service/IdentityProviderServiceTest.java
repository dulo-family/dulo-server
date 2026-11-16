package com.dulo.core.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dulo.core.identity.port.out.IdentityProvidersPort;
import com.dulo.domain.identity.IdentityProvider;
import com.dulo.domain.identity.IdentityProviderType;
import com.dulo.domain.identity.RegistrationMode;
import com.dulo.domain.identity.RoleMapping;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class IdentityProviderServiceTest {

    @Test
    void listsTheConfiguredProviders() {
        final List<IdentityProvider> configured =
                List.of(new IdentityProvider("google", "Google", IdentityProviderType.GOOGLE));
        final IdentityProviderService service =
                new IdentityProviderService(
                        new IdentityProvidersPort() {
                            @Override
                            public List<IdentityProvider> providers() {
                                return configured;
                            }

                            @Override
                            public Optional<RoleMapping> roleMapping(final String providerId) {
                                return Optional.empty();
                            }

                            @Override
                            public RegistrationMode registrationMode() {
                                return RegistrationMode.AUTO;
                            }
                        });

        assertThat(service.identityProviders()).isEqualTo(configured);
        assertThat(IdentityProviderType.MICROSOFT.key()).isEqualTo("microsoft");
    }
}
