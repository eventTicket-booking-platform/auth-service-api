package com.ec7205.event_hub.auth_service_api;

import com.ec7205.event_hub.auth_service_api.config.LocalUserSeeder;
import com.ec7205.event_hub.auth_service_api.dto.request.SystemUserRequestDto;
import com.ec7205.event_hub.auth_service_api.service.SystemUserService;
import com.ec7205.event_hub.auth_service_api.utils.PasswordGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LocalUserSeederTest {
    private final SystemUserService users = mock(SystemUserService.class);
    private final PasswordGenerator passwords = mock(PasswordGenerator.class);

    private ApplicationContextRunner context(String... profiles) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles))
                .withBean(SystemUserService.class, () -> users)
                .withBean(PasswordGenerator.class, () -> passwords)
                .withUserConfiguration(LocalUserSeeder.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void localSeederPreservesHostsAndAddsTheSeparateApplicationAdmin() {
        when(passwords.generatePassword()).thenReturn("generated-host-password");
        context("local").run(context -> {
            assertThat(context).hasSingleBean(LocalUserSeeder.class);
            context.getBean(LocalUserSeeder.class).run();
            ArgumentCaptor<List<SystemUserRequestDto>> hosts = ArgumentCaptor.forClass(List.class);
            ArgumentCaptor<List<SystemUserRequestDto>> admins = ArgumentCaptor.forClass(List.class);
            verify(users).initializeHosts(hosts.capture());
            verify(users).initializeAdmins(admins.capture());
            assertThat(hosts.getValue()).usingRecursiveComparison().isEqualTo(List.of(
                    new SystemUserRequestDto("ABC", "XYZ", "algox1234@gmail.com", "12345678", "0714911257"),
                    new SystemUserRequestDto("Asitha", "Pathirathna ", "asithahpathirathne@gmail.com", "generated-host-password", "0714875865")));
            assertThat(admins.getValue()).usingRecursiveComparison().isEqualTo(List.of(
                    new SystemUserRequestDto("Amiru", "Horadagoda", "eventhub.admin@local.test", "amiru1234", "0717244872")));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "test", ""})
    void seederIsAbsentWithoutLocalProfile(String profile) {
        context(profile.isEmpty() ? new String[0] : new String[]{profile}).run(context -> {
            assertThat(context).doesNotHaveBean(LocalUserSeeder.class);
            verifyNoInteractions(users, passwords);
        });
    }

    @Test
    void productionProfileDisablesSeederEvenIfLocalIsAlsoActive() {
        context("local", "prod").run(context -> {
            assertThat(context).doesNotHaveBean(LocalUserSeeder.class);
            verifyNoInteractions(users, passwords);
        });
    }
}
