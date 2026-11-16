package com.dulo.adapter.rest.error;

import io.swagger.v3.oas.annotations.Hidden;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Answers the errors the servlet container sends to {@code /error} with a problem detail, in place
 * of Spring Boot's error page. These are errors outside the API's controllers, such as a request
 * Spring Security rejects, an invalid authorization request, or an exception nothing handled.
 *
 * <p>Client errors keep the reason their sender gave, so they can be diagnosed from the response.
 * Server errors hide it: the exception is in the server log.
 */
@Hidden
@RestController
class ProblemErrorController implements ErrorController {

    static final String SERVER_ERROR_DETAIL =
            "The server failed to handle the request. The server log has the details.";

    private static HttpStatusCode status(final HttpServletRequest request) {
        // A direct request for the error page has no error to describe.
        return request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)
                        instanceof final Integer code
                ? HttpStatusCode.valueOf(code)
                : HttpStatus.NOT_FOUND;
    }

    private static @Nullable String detail(
            final HttpServletRequest request, final HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return SERVER_ERROR_DETAIL;
        }
        return request.getAttribute(RequestDispatcher.ERROR_MESSAGE) instanceof final String message
                        && !message.isBlank()
                ? message
                : null;
    }

    private static @Nullable URI type(final HttpStatusCode status) {
        if (status.value() == HttpStatus.UNAUTHORIZED.value()) {
            return ProblemTypes.UNAUTHORIZED;
        }
        if (status.value() == HttpStatus.FORBIDDEN.value()) {
            return ProblemTypes.FORBIDDEN;
        }
        return null;
    }

    private static @Nullable URI instance(final HttpServletRequest request) {
        if (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof final String uri) {
            try {
                return URI.create(uri);
            } catch (final IllegalArgumentException _) {
                return null;
            }
        }
        return null;
    }

    @RequestMapping("${server.error.path:/error}")
    ResponseEntity<ProblemDetail> error(final HttpServletRequest request) {
        final HttpStatusCode status = status(request);
        final ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(status, detail(request, status));
        final URI type = type(status);
        if (type != null) {
            problem.setType(type);
        }
        final URI instance = instance(request);
        if (instance != null) {
            problem.setInstance(instance);
        }
        // Set the content type, so a browser asking for HTML gets the problem too.
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
