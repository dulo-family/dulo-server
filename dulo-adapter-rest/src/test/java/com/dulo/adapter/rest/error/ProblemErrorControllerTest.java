package com.dulo.adapter.rest.error;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;
import java.util.Objects;

class ProblemErrorControllerTest {

    private final ProblemErrorController controller = new ProblemErrorController();

    private static MockHttpServletRequest error(
            final int status, final String message, final String uri) {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        request.setAttribute(RequestDispatcher.ERROR_MESSAGE, message);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, uri);
        return request;
    }

    private ProblemDetail problem(final MockHttpServletRequest request) {
        final ResponseEntity<ProblemDetail> response = this.controller.error(request);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        final ProblemDetail problem = Objects.requireNonNull(response.getBody());
        assertThat(response.getStatusCode().value()).isEqualTo(problem.getStatus());
        return problem;
    }

    @Test
    void clientErrorsKeepTheirReason() {
        final ProblemDetail problem =
                this.problem(
                        error(
                                400,
                                "[invalid_request] OAuth 2.0 Parameter: client_id",
                                "/oauth2/authorize"));

        assertThat(problem.getStatus()).isEqualTo(400);
        // No type means about:blank: the status says it all.
        assertThat(problem.getType()).isNull();
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        assertThat(problem.getDetail())
                .isEqualTo("[invalid_request] OAuth 2.0 Parameter: client_id");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/oauth2/authorize"));
    }

    @Test
    void authenticationAndAccessErrorsHaveDuloTypes() {
        assertThat(this.problem(error(401, "", "/a")).getType())
                .isEqualTo(ProblemTypes.UNAUTHORIZED);
        final ProblemDetail forbidden = this.problem(error(403, "", "/b"));
        assertThat(forbidden.getType()).isEqualTo(ProblemTypes.FORBIDDEN);
        assertThat(forbidden.getDetail()).isNull();
    }

    @Test
    void serverErrorsHideTheirReason() {
        final ProblemDetail problem =
                this.problem(error(500, "java.lang.IllegalStateException: secret", "/x"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).isEqualTo(ProblemErrorController.SERVER_ERROR_DETAIL);
    }

    @Test
    void theErrorPageItselfIsNotFound() {
        final ProblemDetail problem = this.problem(new MockHttpServletRequest("GET", "/error"));

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getInstance()).isNull();
    }

    @Test
    void unparsablePathsAreLeftOut() {
        assertThat(this.problem(error(400, "Bad", "/a b")).getInstance()).isNull();
    }
}
