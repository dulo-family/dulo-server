package com.dulo.adapter.rest.setup;

import com.dulo.adapter.rest.error.SetupRequiredException;
import com.dulo.core.setup.port.in.GetSetupStatusUseCase;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;

/** Answers 503 to API calls until the initial setup is done. */
class SetupRequiredInterceptor implements HandlerInterceptor {

    private final GetSetupStatusUseCase status;

    SetupRequiredInterceptor(final GetSetupStatusUseCase status) {
        this.status = status;
    }

    @Override
    public boolean preHandle(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final Object handler) {
        if (this.status.isSetupPending()) {
            throw new SetupRequiredException();
        }
        return true;
    }
}
