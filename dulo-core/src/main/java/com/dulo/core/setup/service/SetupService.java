package com.dulo.core.setup.service;

import com.dulo.core.access.port.out.RoleRepository;
import com.dulo.core.access.port.out.UserRepository;
import com.dulo.core.access.service.RoleFields;
import com.dulo.core.access.service.UserFields;
import com.dulo.core.audit.recording.ChangeRecorder;
import com.dulo.core.audit.recording.ChangeSet;
import com.dulo.core.identity.port.out.IdentityProvidersPort;
import com.dulo.core.setup.port.in.CompleteSetupUseCase;
import com.dulo.core.setup.port.in.GetSetupConfigurationUseCase;
import com.dulo.core.setup.port.in.GetSetupDefaultsUseCase;
import com.dulo.core.setup.port.in.GetSetupStatusUseCase;
import com.dulo.core.setup.port.in.InvalidSetupTokenException;
import com.dulo.core.setup.port.in.SetupCommand;
import com.dulo.core.setup.port.in.SetupCompletedException;
import com.dulo.core.setup.port.in.SetupDefaults;
import com.dulo.core.setup.port.out.ConfigurationReportPort;
import com.dulo.core.setup.port.out.SetupTokenPort;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.core.tree.port.in.TreeView;
import com.dulo.core.tree.port.out.FeatureOverridesPort;
import com.dulo.core.tree.port.out.TreeSettingsRepository;
import com.dulo.core.tree.service.TreeFields;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.access.DefaultRoles;
import com.dulo.domain.access.Role;
import com.dulo.domain.access.RoleKey;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.feature.FeatureOverrides;
import com.dulo.domain.feature.FeatureState;
import com.dulo.domain.identity.IdentityProvider;
import com.dulo.domain.identity.RoleMapping;
import com.dulo.domain.tree.TreeSettings;
import com.dulo.domain.user.User;
import com.dulo.domain.user.UserId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * The initial setup: while no root user exists the server is in setup mode, and the first user to
 * complete the setup with the setup token becomes root.
 */
public class SetupService
        implements GetSetupStatusUseCase,
                GetSetupDefaultsUseCase,
                GetSetupConfigurationUseCase,
                CompleteSetupUseCase {

    private static final System.Logger LOG = System.getLogger(SetupService.class.getName());

    private static final Set<RoleKey> BUILT_IN_ROLES =
            DefaultRoles.all().stream().map(Role::key).collect(Collectors.toUnmodifiableSet());

    private final UserRepository users;
    private final RoleRepository roles;
    private final TreeSettingsRepository trees;
    private final IdentityProvidersPort providers;
    private final FeatureOverridesPort featureOverrides;
    private final SetupTokenPort setupToken;
    private final ConfigurationReportPort configurationReport;
    private final ChangeRecorder recorder;
    private final UnitOfWork unitOfWork;

    /** The setup cannot be undone, so once it is done the database is not asked again. */
    private final AtomicBoolean done = new AtomicBoolean();

    public SetupService(
            final UserRepository users,
            final RoleRepository roles,
            final TreeSettingsRepository trees,
            final IdentityProvidersPort providers,
            final FeatureOverridesPort featureOverrides,
            final SetupTokenPort setupToken,
            final ConfigurationReportPort configurationReport,
            final ChangeRecorder recorder,
            final UnitOfWork unitOfWork) {
        this.users = users;
        this.roles = roles;
        this.trees = trees;
        this.providers = providers;
        this.featureOverrides = featureOverrides;
        this.setupToken = setupToken;
        this.configurationReport = configurationReport;
        this.recorder = recorder;
        this.unitOfWork = unitOfWork;
    }

    /** The built-in roles, changed or extended by the command's roles. */
    private static List<Role> rolesToStore(final SetupCommand command) {
        final Map<RoleKey, Role> roles = new LinkedHashMap<>();
        DefaultRoles.all().forEach(role -> roles.put(role.key(), role));
        final Set<RoleKey> given = new HashSet<>();
        for (final SetupCommand.SetupRole role : command.roles()) {
            if (!given.add(role.key())) {
                throw new IllegalArgumentException(
                        "The role '%s' is defined twice".formatted(role.key()));
            }
            roles.put(
                    role.key(),
                    new Role(
                            role.key(),
                            role.name(),
                            BUILT_IN_ROLES.contains(role.key()),
                            role.permissions(),
                            0));
        }
        return List.copyOf(roles.values());
    }

    /** A new tree, or the existing one if a tree was stored without a setup. */
    private static TreeSettings tree(
            final Optional<TreeSettings> existingTree, final SetupCommand command) {
        return existingTree
                .map(existing ->
                        new TreeSettings(
                                existing.id(),
                                command.treeName(),
                                command.treeDescription(),
                                command.enabledFeatures(),
                                existing.version()))
                .orElseGet(() ->
                        TreeSettings.create(
                                command.treeName(),
                                command.treeDescription(),
                                command.enabledFeatures()));
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

    @Override
    public SetupDefaults defaults(final String setupToken) {
        this.requirePendingWithToken(setupToken);
        final FeatureOverrides overrides = this.featureOverrides.overrides();
        final List<FeatureState> features =
                Arrays.stream(Feature.values())
                        .filter(Feature::toggleable)
                        .map(feature ->
                                overrides
                                        .forcedState(feature)
                                        .map(forced -> new FeatureState(feature, forced, true))
                                        .orElseGet(() -> new FeatureState(feature, true, false)))
                        .toList();
        final List<SetupDefaults.ProviderRoles> providers =
                this.providers.providers().stream()
                        .map(provider ->
                                new SetupDefaults.ProviderRoles(
                                        provider, this.roleMapping(provider)))
                        .toList();
        return new SetupDefaults(
                DefaultRoles.all(), features, providers, this.providers.registrationMode());
    }

    @Override
    public String configuration(final String setupToken) {
        this.requirePendingWithToken(setupToken);
        return this.configurationReport.report();
    }

    /**
     * Synchronized, so two requests can't both pass the "still pending" check. Dulo runs as a
     * single instance.
     */
    @Override
    public synchronized TreeView complete(
            final UserId caller, final String setupToken, final SetupCommand command) {
        this.requirePendingWithToken(setupToken);
        final List<Role> newRoles = rolesToStore(command);
        this.checkMappedRolesExist(newRoles);
        final FeatureOverrides overrides = this.featureOverrides.overrides();
        final TreeSettings tree =
                this.unitOfWork.write(() -> {
                    if (this.users.existsRoot()) {
                        throw new SetupCompletedException();
                    }
                    final User user =
                            this.users
                                    .findById(caller)
                                    .orElseThrow(() ->
                                            new AccessDeniedException(
                                                    AccessDeniedException.Reason.UNKNOWN_USER,
                                                    "The user does not exist"));
                    final ChangeSet changes = this.recorder.changeSet(caller);
                    final Optional<TreeSettings> existingTree = this.trees.find();
                    final TreeSettings stored = this.trees.save(tree(existingTree, command));
                    existingTree.ifPresentOrElse(
                            before -> changes.changed(TreeFields.INSTANCE, before, stored),
                            () -> changes.created(TreeFields.INSTANCE, stored));
                    newRoles.forEach(role -> {
                        final Optional<Role> existingRole = this.roles.find(role.key());
                        final Role storedRole =
                                this.roles.save(
                                        new Role(
                                                role.key(),
                                                role.name(),
                                                role.builtIn(),
                                                role.permissions(),
                                                existingRole.map(Role::version).orElse(0L)));
                        existingRole.ifPresentOrElse(
                                before -> changes.changed(RoleFields.INSTANCE, before, storedRole),
                                () -> changes.created(RoleFields.INSTANCE, storedRole));
                    });
                    changes.changed(UserFields.INSTANCE, user, this.users.save(user.asRoot()));
                    return stored;
                });
        this.done.set(true);
        LOG.log(
                System.Logger.Level.INFO,
                "Initial setup done: created the tree \"{0}\" and {1} roles; user {2} is root",
                tree.name(),
                newRoles.size(),
                caller);
        return new TreeView(tree, tree.featureStates(overrides));
    }

    private void requirePendingWithToken(final String setupToken) {
        if (!this.isSetupPending()) {
            throw new SetupCompletedException();
        }
        // Compared in constant time, so the response time doesn't reveal how much of it matched.
        if (!MessageDigest.isEqual(
                this.setupToken.token().getBytes(StandardCharsets.UTF_8),
                setupToken.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidSetupTokenException();
        }
    }

    private RoleMapping roleMapping(final IdentityProvider provider) {
        return this.providers.roleMapping(provider.id()).orElse(RoleMapping.none());
    }

    /** Every role a provider's configuration maps to must exist, or the mapping grants nothing. */
    private void checkMappedRolesExist(final List<Role> roles) {
        final Set<RoleKey> defined = roles.stream().map(Role::key).collect(Collectors.toSet());
        for (final IdentityProvider provider : this.providers.providers()) {
            final RoleMapping mapping = this.roleMapping(provider);
            final Set<RoleKey> used = new HashSet<>();
            mapping.byClaimValue().values().forEach(used::addAll);
            mapping.defaultRoleIfAny().ifPresent(used::add);
            used.stream()
                    .filter(key -> !defined.contains(key))
                    .sorted(Comparator.comparing(RoleKey::value))
                    .findFirst()
                    .ifPresent(key -> {
                        throw new IllegalArgumentException(
                                ("The identity provider '%s' maps users to the role '%s', which"
                                                + " the setup doesn't define")
                                        .formatted(provider.id(), key));
                    });
        }
    }
}
