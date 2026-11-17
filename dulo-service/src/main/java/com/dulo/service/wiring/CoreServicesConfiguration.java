package com.dulo.service.wiring;

import com.dulo.core.access.port.out.RoleRepository;
import com.dulo.core.access.port.out.UserRepository;
import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.access.service.CurrentUserService;
import com.dulo.core.access.service.RoleAdministrationService;
import com.dulo.core.access.service.UserAdministrationService;
import com.dulo.core.audit.port.out.ChangeEventRepository;
import com.dulo.core.audit.recording.ChangeRecorder;
import com.dulo.core.audit.service.AuditLogService;
import com.dulo.core.identity.port.out.IdentityProvidersPort;
import com.dulo.core.identity.port.out.SigningKeyRepository;
import com.dulo.core.identity.service.IdentityProviderService;
import com.dulo.core.identity.service.SignInService;
import com.dulo.core.identity.service.SigningKeyService;
import com.dulo.core.person.port.out.PersonRepository;
import com.dulo.core.person.service.PersonService;
import com.dulo.core.place.port.out.PlaceRepository;
import com.dulo.core.place.service.PlaceService;
import com.dulo.core.setup.port.out.ConfigurationReportPort;
import com.dulo.core.setup.port.out.SetupTokenPort;
import com.dulo.core.setup.service.SetupService;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.core.tree.port.out.FeatureOverridesPort;
import com.dulo.core.tree.port.out.TreeSettingsRepository;
import com.dulo.core.tree.service.TreeService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Creates the framework-free application services and connects them to their ports. */
@Configuration(proxyBeanMethods = false)
public class CoreServicesConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ChangeRecorder changeRecorder(final ChangeEventRepository events, final Clock clock) {
        return new ChangeRecorder(events, clock);
    }

    @Bean
    AccessPolicy accessPolicy(
            final UserRepository users,
            final RoleRepository roles,
            final TreeSettingsRepository trees,
            final FeatureOverridesPort featureOverrides) {
        return new AccessPolicy(users, roles, trees, featureOverrides);
    }

    @Bean
    AuditLogService auditLogService(
            final ChangeEventRepository events,
            final AccessPolicy accessPolicy,
            final UnitOfWork unitOfWork) {
        return new AuditLogService(events, accessPolicy, unitOfWork);
    }

    @Bean
    CurrentUserService currentUserService(
            final UserRepository users,
            final AccessPolicy accessPolicy,
            final UnitOfWork unitOfWork) {
        return new CurrentUserService(users, accessPolicy, unitOfWork);
    }

    @Bean
    IdentityProviderService identityProviderService(final IdentityProvidersPort providers) {
        return new IdentityProviderService(providers);
    }

    @Bean
    PersonService personService(
            final PersonRepository people,
            final ChangeEventRepository events,
            final AccessPolicy accessPolicy,
            final ChangeRecorder recorder,
            final UnitOfWork unitOfWork) {
        return new PersonService(people, events, accessPolicy, recorder, unitOfWork);
    }

    @Bean
    PlaceService placeService(
            final PlaceRepository places,
            final ChangeEventRepository events,
            final AccessPolicy accessPolicy,
            final ChangeRecorder recorder,
            final UnitOfWork unitOfWork) {
        return new PlaceService(places, events, accessPolicy, recorder, unitOfWork);
    }

    @Bean
    RoleAdministrationService roleAdministrationService(
            final RoleRepository roles,
            final UserRepository users,
            final IdentityProvidersPort providers,
            final AccessPolicy accessPolicy,
            final ChangeRecorder recorder,
            final UnitOfWork unitOfWork) {
        return new RoleAdministrationService(
                roles, users, providers, accessPolicy, recorder, unitOfWork);
    }

    @Bean
    SetupService setupService(
            final UserRepository users,
            final RoleRepository roles,
            final TreeSettingsRepository trees,
            final IdentityProvidersPort providers,
            final FeatureOverridesPort featureOverrides,
            final SetupTokenPort setupToken,
            final ConfigurationReportPort configurationReport,
            final ChangeRecorder recorder,
            final UnitOfWork unitOfWork) {
        return new SetupService(
                users,
                roles,
                trees,
                providers,
                featureOverrides,
                setupToken,
                configurationReport,
                recorder,
                unitOfWork);
    }

    @Bean
    SignInService signInService(
            final UserRepository users,
            final IdentityProvidersPort providers,
            final ChangeRecorder recorder,
            final UnitOfWork unitOfWork,
            final Clock clock) {
        return new SignInService(users, providers, recorder, unitOfWork, clock);
    }

    @Bean
    SigningKeyService signingKeyService(
            final SigningKeyRepository keys, final UnitOfWork unitOfWork) {
        return new SigningKeyService(keys, unitOfWork);
    }

    @Bean
    UserAdministrationService userAdministrationService(
            final UserRepository users,
            final RoleRepository roles,
            final AccessPolicy accessPolicy,
            final ChangeRecorder recorder,
            final UnitOfWork unitOfWork) {
        return new UserAdministrationService(users, roles, accessPolicy, recorder, unitOfWork);
    }

    @Bean
    TreeService treeService(
            final TreeSettingsRepository trees,
            final FeatureOverridesPort featureOverrides,
            final AccessPolicy accessPolicy,
            final ChangeRecorder recorder,
            final UnitOfWork unitOfWork) {
        return new TreeService(trees, featureOverrides, accessPolicy, recorder, unitOfWork);
    }
}
