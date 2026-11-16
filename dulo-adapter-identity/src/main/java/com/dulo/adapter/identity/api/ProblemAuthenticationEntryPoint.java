package com.dulo.adapter.identity.api;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Answers API calls without a valid token with 401, the {@code WWW-Authenticate} header of RFC
 * 6750, and an RFC 9457 problem body like every other API error.
 */
class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String PROBLEM_TYPE = "urn:dulo:problem:unauthorized";

    private static final String BODY =
            """
            {"type":"%s","title":"Unauthorized","status":401,\
            "detail":"Sign in and send the access token in the Authorization header."}\
            """
                    .formatted(PROBLEM_TYPE);

    private final AuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final AuthenticationException authException)
            throws IOException, ServletException {
        this.bearer.commence(request, response, authException);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }
}
