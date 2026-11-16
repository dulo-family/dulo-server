package com.dulo.core.access.service;

import com.dulo.core.access.port.in.CurrentUserView;
import com.dulo.core.access.port.in.GetCurrentUserUseCase;
import com.dulo.core.access.port.out.UserRepository;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.access.AccessDeniedException.Reason;
import com.dulo.domain.user.User;
import com.dulo.domain.user.UserId;

public class CurrentUserService implements GetCurrentUserUseCase {

    private final UserRepository users;
    private final AccessPolicy accessPolicy;
    private final UnitOfWork unitOfWork;

    public CurrentUserService(
            final UserRepository users,
            final AccessPolicy accessPolicy,
            final UnitOfWork unitOfWork) {
        this.users = users;
        this.accessPolicy = accessPolicy;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public CurrentUserView currentUser(final UserId caller) {
        return this.unitOfWork.read(() -> {
            final User user =
                    this.users
                            .findById(caller)
                            .orElseThrow(() ->
                                    new AccessDeniedException(
                                            Reason.UNKNOWN_USER, "The user does not exist"));
            return new CurrentUserView(user, this.accessPolicy.effectivePermissions(user));
        });
    }
}
