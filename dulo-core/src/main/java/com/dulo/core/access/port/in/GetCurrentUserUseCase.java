package com.dulo.core.access.port.in;

import com.dulo.domain.user.UserId;

/** Who the caller is and what they may do. */
public interface GetCurrentUserUseCase {

    /**
     * Returns the user even while they are pending or disabled, so clients can tell them why they
     * cannot do anything.
     *
     * @throws com.dulo.domain.access.AccessDeniedException if the user does not exist
     */
    CurrentUserView currentUser(UserId caller);
}
