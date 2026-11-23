package com.dulo.core.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.server.port.in.ServerInfo;
import com.dulo.core.testing.DirectUnitOfWork;
import com.dulo.core.testing.InMemoryRoles;
import com.dulo.core.testing.InMemoryTrees;
import com.dulo.core.testing.InMemoryUsers;
import com.dulo.core.testing.Users;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.access.DefaultRoles;
import com.dulo.domain.feature.FeatureOverrides;
import com.dulo.domain.user.UserStatus;

import org.junit.jupiter.api.Test;

import java.util.Map;

class ServerInfoServiceTest {

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryTrees trees = new InMemoryTrees(null);
    private final FeatureOverrides overrides = new FeatureOverrides(Map.of());
    private final ServerInfoService service =
            new ServerInfoService(
                    new ServerInfo("1.2.3"),
                    new AccessPolicy(
                            this.users,
                            new InMemoryRoles(DefaultRoles.all()),
                            this.trees,
                            () -> this.overrides),
                    new DirectUnitOfWork());

    @Test
    void anyActiveUserMayReadTheVersion() {
        // No roles at all, but active.
        final var active = this.users.save(Users.user(UserStatus.ACTIVE)).id();

        assertThat(this.service.getServerInfo(active)).isEqualTo(new ServerInfo("1.2.3"));
    }

    @Test
    void inactiveUsersMayNot() {
        final var pending = this.users.save(Users.user(UserStatus.PENDING)).id();
        final var disabled = this.users.save(Users.user(UserStatus.DISABLED)).id();

        assertThatThrownBy(() -> this.service.getServerInfo(pending))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> this.service.getServerInfo(disabled))
                .isInstanceOf(AccessDeniedException.class);
    }
}
