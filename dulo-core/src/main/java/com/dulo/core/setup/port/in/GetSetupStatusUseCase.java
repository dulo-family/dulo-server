package com.dulo.core.setup.port.in;

/** Whether the initial setup still has to be done. */
public interface GetSetupStatusUseCase {

    /** True until the initial setup has created the root user. */
    boolean isSetupPending();
}
