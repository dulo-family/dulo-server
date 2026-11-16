package com.dulo.core.tree.port.in;

import com.dulo.domain.user.UserId;

import java.util.Optional;

public interface GetTreeUseCase {

    /**
     * The tree, or empty when the initial setup has not created it yet.
     *
     * @throws com.dulo.domain.access.AccessDeniedException if the caller is not an active user
     */
    Optional<TreeView> getTree(UserId caller);
}
