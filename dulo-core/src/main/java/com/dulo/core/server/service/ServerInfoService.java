package com.dulo.core.server.service;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.server.port.in.GetServerInfoUseCase;
import com.dulo.core.server.port.in.ServerInfo;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.domain.user.UserId;

public class ServerInfoService implements GetServerInfoUseCase {

    private final ServerInfo info;
    private final AccessPolicy accessPolicy;
    private final UnitOfWork unitOfWork;

    public ServerInfoService(
            final ServerInfo info, final AccessPolicy accessPolicy, final UnitOfWork unitOfWork) {
        this.info = info;
        this.accessPolicy = accessPolicy;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public ServerInfo getServerInfo(final UserId caller) {
        return this.unitOfWork.read(() -> {
            // Every active user may know it; anonymous callers can't learn the exact version.
            this.accessPolicy.requireActive(caller);
            return this.info;
        });
    }
}
