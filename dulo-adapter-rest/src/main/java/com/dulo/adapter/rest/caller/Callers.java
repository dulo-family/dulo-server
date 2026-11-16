package com.dulo.adapter.rest.caller;

import com.dulo.domain.user.UserId;

import java.security.Principal;

/**
 * The Dulo user behind a request. The security layer checks the access token before a controller
 * runs and names the principal after the token's subject, the Dulo user id. Controllers take the
 * plain Servlet {@link Principal}, so the REST adapter needs no security framework.
 */
public final class Callers {

    private Callers() {}

    public static UserId id(final Principal principal) {
        return UserId.parse(principal.getName());
    }
}
