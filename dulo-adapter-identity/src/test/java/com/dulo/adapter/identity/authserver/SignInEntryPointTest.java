package com.dulo.adapter.identity.authserver;

import static org.assertj.core.api.Assertions.assertThat;

import com.dulo.adapter.identity.settings.IdentityAdapterSettings;
import com.dulo.adapter.identity.settings.TestSettings;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class SignInEntryPointTest {

    private static final IdentityAdapterSettings TWO_PROVIDERS =
            TestSettings.settings(
                    TestSettings.provider("google", "s"), TestSettings.provider("pocketid", "s"));

    private static @Nullable String redirect(
            final IdentityAdapterSettings settings, final @Nullable String idp) throws Exception {
        final MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/oauth2/authorize");
        if (idp != null) {
            request.setParameter(SignInEntryPoint.IDP_PARAMETER, idp);
        }
        final MockHttpServletResponse response = new MockHttpServletResponse();
        new SignInEntryPoint(settings)
                .commence(request, response, new InsufficientAuthenticationException("test"));
        return response.getRedirectedUrl();
    }

    @Test
    void singleProviderSkipsTheSignInPage() throws Exception {
        assertThat(redirect(TestSettings.settings(TestSettings.provider("google", "s")), null))
                .isEqualTo("https://tree.example.com/oauth2/authorization/google");
    }

    @Test
    void hintPicksTheProvider() throws Exception {
        assertThat(redirect(TWO_PROVIDERS, "pocketid"))
                .isEqualTo("https://tree.example.com/oauth2/authorization/pocketid");
    }

    @Test
    void otherwiseTheUserPicksOnTheSignInPage() throws Exception {
        assertThat(redirect(TWO_PROVIDERS, null)).isEqualTo(TestSettings.SIGN_IN_URL.toString());
        assertThat(redirect(TWO_PROVIDERS, "ghost")).isEqualTo(TestSettings.SIGN_IN_URL.toString());
        assertThat(redirect(TestSettings.settings(), null))
                .isEqualTo(TestSettings.SIGN_IN_URL.toString());
    }
}
