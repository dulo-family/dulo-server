package com.dulo.core.setup.service;

import com.dulo.core.access.port.out.UserRepository;
import com.dulo.core.setup.port.in.GetSetupStatusUseCase;
import com.dulo.core.shared.port.out.UnitOfWork;

import java.util.concurrent.atomic.AtomicBoolean;

public class SetupService implements GetSetupStatusUseCase {

    private final UserRepository users;
    private final UnitOfWork unitOfWork;

    /** The setup cannot be undone, so once it is done the database is not asked again. */
    private final AtomicBoolean done = new AtomicBoolean();

    public SetupService(final UserRepository users, final UnitOfWork unitOfWork) {
        this.users = users;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public boolean isSetupPending() {
        if (this.done.get()) {
            return false;
        }
        if (this.unitOfWork.read(this.users::existsRoot)) {
            this.done.set(true);
            return false;
        }
        return true;
    }
}
