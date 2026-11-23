package com.dulo.core.server.port.in;

import com.dulo.domain.user.UserId;

public interface GetServerInfoUseCase {

    /**
     * The server's version, for clients that show what is running.
     *
     * @throws com.dulo.domain.access.AccessDeniedException if the caller is not an active user
     */
    ServerInfo getServerInfo(UserId caller);
}
