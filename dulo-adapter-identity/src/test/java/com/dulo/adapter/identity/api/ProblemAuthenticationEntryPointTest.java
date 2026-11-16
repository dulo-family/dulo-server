package com.dulo.adapter.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class ProblemAuthenticationEntryPointTest {

    @Test
    void answersWithAProblem() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        new ProblemAuthenticationEntryPoint()
                .commence(
                        new MockHttpServletRequest("GET", "/api/v1/tree"),
                        response,
                        new InsufficientAuthenticationException("no token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer");
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"type\":\"urn:dulo:problem:unauthorized\"")
                .contains("\"status\":401");
    }
}
