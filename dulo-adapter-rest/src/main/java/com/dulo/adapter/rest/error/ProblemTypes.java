package com.dulo.adapter.rest.error;

import java.net.URI;

/** Problem type URIs (RFC 9457) returned by the API. Clients can switch on these values. */
public final class ProblemTypes {

    public static final URI TREE_NOT_INITIALIZED =
            URI.create("urn:dulo:problem:tree-not-initialized");
    public static final URI CONCURRENT_MODIFICATION =
            URI.create("urn:dulo:problem:concurrent-modification");

    public static final URI UNAUTHORIZED = URI.create("urn:dulo:problem:unauthorized");
    public static final URI FORBIDDEN = URI.create("urn:dulo:problem:forbidden");
    public static final URI FEATURE_DISABLED = URI.create("urn:dulo:problem:feature-disabled");
    public static final URI ACCOUNT_PENDING = URI.create("urn:dulo:problem:account-pending");
    public static final URI ACCOUNT_DISABLED = URI.create("urn:dulo:problem:account-disabled");

    public static final URI SETUP_REQUIRED = URI.create("urn:dulo:problem:setup-required");
    public static final URI SETUP_COMPLETED = URI.create("urn:dulo:problem:setup-completed");
    public static final URI INVALID_SETUP_TOKEN =
            URI.create("urn:dulo:problem:invalid-setup-token");

    public static final URI VALIDATION = URI.create("urn:dulo:problem:validation");

    private ProblemTypes() {}
}
