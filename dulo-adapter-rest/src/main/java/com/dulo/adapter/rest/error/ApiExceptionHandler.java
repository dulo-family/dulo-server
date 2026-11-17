package com.dulo.adapter.rest.error;

import com.dulo.adapter.rest.audit.ChangeEventResponse;
import com.dulo.core.access.port.in.RoleExistsException;
import com.dulo.core.access.port.in.RoleInUseException;
import com.dulo.core.setup.port.in.InvalidSetupTokenException;
import com.dulo.core.setup.port.in.SetupCompletedException;
import com.dulo.core.shared.port.in.HasLinksException;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.user.UserId;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

/** Maps exceptions to RFC 9457 problem details. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static ProblemDetail problem(
            final HttpStatus status, final URI type, final String title, final Exception e) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setType(type);
        problem.setTitle(title);
        return problem;
    }

    @ExceptionHandler
    ProblemDetail treeNotInitialized(final TreeNotInitializedException e) {
        return problem(
                HttpStatus.NOT_FOUND, ProblemTypes.TREE_NOT_INITIALIZED, "Tree not initialized", e);
    }

    /** A token for a user who no longer exists is as good as no token. */
    @ExceptionHandler
    ProblemDetail accessDenied(final AccessDeniedException e) {
        return switch (e.reason()) {
            case UNKNOWN_USER ->
                    problem(HttpStatus.UNAUTHORIZED, ProblemTypes.UNAUTHORIZED, "Unauthorized", e);
            case NOT_PERMITTED ->
                    problem(HttpStatus.FORBIDDEN, ProblemTypes.FORBIDDEN, "Forbidden", e);
            case FEATURE_DISABLED ->
                    problem(
                            HttpStatus.FORBIDDEN,
                            ProblemTypes.FEATURE_DISABLED,
                            "Feature disabled",
                            e);
            case ACCOUNT_PENDING ->
                    problem(
                            HttpStatus.FORBIDDEN,
                            ProblemTypes.ACCOUNT_PENDING,
                            "Account pending approval",
                            e);
            case ACCOUNT_DISABLED ->
                    problem(
                            HttpStatus.FORBIDDEN,
                            ProblemTypes.ACCOUNT_DISABLED,
                            "Account disabled",
                            e);
        };
    }

    @ExceptionHandler
    ProblemDetail setupRequired(final SetupRequiredException e) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE, ProblemTypes.SETUP_REQUIRED, "Setup required", e);
    }

    @ExceptionHandler
    ProblemDetail setupCompleted(final SetupCompletedException e) {
        return problem(HttpStatus.CONFLICT, ProblemTypes.SETUP_COMPLETED, "Setup completed", e);
    }

    @ExceptionHandler
    ProblemDetail invalidSetupToken(final InvalidSetupTokenException e) {
        return problem(
                HttpStatus.FORBIDDEN, ProblemTypes.INVALID_SETUP_TOKEN, "Invalid setup token", e);
    }

    /** The domain checks values with {@link IllegalArgumentException}s that say what's wrong. */
    @ExceptionHandler
    ProblemDetail invalid(final IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION, "Invalid request", e);
    }

    /** The If-Match version is outdated: someone changed the resource since it was read. */
    @ExceptionHandler
    ProblemDetail concurrentModification(final ConcurrentModificationException e) {
        return problem(
                HttpStatus.PRECONDITION_FAILED,
                ProblemTypes.CONCURRENT_MODIFICATION,
                "Concurrent modification",
                e);
    }

    @ExceptionHandler
    ProblemDetail preconditionRequired(final PreconditionRequiredException e) {
        return problem(
                HttpStatus.PRECONDITION_REQUIRED,
                ProblemTypes.PRECONDITION_REQUIRED,
                "If-Match required",
                e);
    }

    @ExceptionHandler
    ProblemDetail notFound(final NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, ProblemTypes.NOT_FOUND, "Not found", e);
    }

    @ExceptionHandler
    ProblemDetail roleExists(final RoleExistsException e) {
        return problem(HttpStatus.CONFLICT, ProblemTypes.ROLE_EXISTS, "Role exists", e);
    }

    /** Says what uses the role, so an administrator can free it. */
    @ExceptionHandler
    ProblemDetail roleInUse(final RoleInUseException e) {
        final ProblemDetail problem =
                problem(HttpStatus.CONFLICT, ProblemTypes.ROLE_IN_USE, "Role in use", e);
        problem.setProperty("users", e.users().stream().map(UserId::toString).toList());
        problem.setProperty("identityProviders", e.identityProviders());
        return problem;
    }

    /** Lists the references, so the client can warn about what a confirmed deletion removes. */
    @ExceptionHandler
    ProblemDetail hasLinks(final HasLinksException e) {
        final ProblemDetail problem =
                problem(HttpStatus.CONFLICT, ProblemTypes.HAS_LINKS, "Still linked", e);
        problem.setProperty(
                "links", e.links().stream().map(ChangeEventResponse.LinkResponse::from).toList());
        return problem;
    }
}
