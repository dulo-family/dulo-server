package com.dulo.core.setup.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dulo.core.testing.DirectUnitOfWork;
import com.dulo.core.testing.InMemoryUsers;
import com.dulo.core.testing.Users;
import com.dulo.domain.user.UserStatus;

import org.junit.jupiter.api.Test;

class SetupServiceTest {

    @Test
    void pendingUntilARootUserExists() {
        final InMemoryUsers users = new InMemoryUsers();
        final SetupService service = new SetupService(users, new DirectUnitOfWork());

        assertThat(service.isSetupPending()).isTrue();
        users.save(Users.user(UserStatus.ACTIVE));
        assertThat(service.isSetupPending()).isTrue();

        users.save(Users.user(UserStatus.ACTIVE).asRoot());
        assertThat(service.isSetupPending()).isFalse();
        assertThat(service.isSetupPending()).isFalse();
    }
}
