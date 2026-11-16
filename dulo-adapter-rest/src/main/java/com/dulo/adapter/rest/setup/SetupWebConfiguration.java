package com.dulo.adapter.rest.setup;

import com.dulo.core.setup.port.in.GetSetupStatusUseCase;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Closes the API until the initial setup is done. */
@Configuration(proxyBeanMethods = false)
class SetupWebConfiguration implements WebMvcConfigurer {

    /**
     * What works during the setup: the setup itself, what the sign-in page needs, the signed-in
     * user who will become root, and the API description.
     */
    static final String[] OPEN_DURING_SETUP = {
        "/api/v1/setup/**", "/api/v1/identity-providers", "/api/v1/me", "/api/v1/openapi.json",
    };

    private final GetSetupStatusUseCase status;

    SetupWebConfiguration(final GetSetupStatusUseCase status) {
        this.status = status;
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(new SetupRequiredInterceptor(this.status))
                .addPathPatterns("/api/**")
                .excludePathPatterns(OPEN_DURING_SETUP);
    }
}
