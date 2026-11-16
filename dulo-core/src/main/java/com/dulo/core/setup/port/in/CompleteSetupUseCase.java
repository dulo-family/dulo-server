package com.dulo.core.setup.port.in;

import com.dulo.core.tree.port.in.TreeView;
import com.dulo.domain.user.UserId;

public interface CompleteSetupUseCase {

    /**
     * Creates the tree and the roles, and makes the caller the root user, in one step.
     *
     * @param caller the signed-in user who becomes root, whatever their status
     * @param setupToken the one-time setup token
     * @return the new tree
     * @throws SetupCompletedException if the setup is done
     * @throws InvalidSetupTokenException if the token is wrong
     * @throws IllegalArgumentException if the command is invalid, e.g. a provider maps a claim to a
     *     role the setup doesn't define
     * @throws com.dulo.domain.access.AccessDeniedException if the caller doesn't exist
     */
    TreeView complete(UserId caller, String setupToken, SetupCommand command);
}
