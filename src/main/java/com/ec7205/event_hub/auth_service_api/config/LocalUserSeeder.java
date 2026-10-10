package com.ec7205.event_hub.auth_service_api.config;

import com.ec7205.event_hub.auth_service_api.dto.request.SystemUserRequestDto;
import com.ec7205.event_hub.auth_service_api.service.SystemUserService;
import com.ec7205.event_hub.auth_service_api.utils.PasswordGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@Profile("local & !prod")
@RequiredArgsConstructor
public class LocalUserSeeder implements CommandLineRunner {

    private final SystemUserService service;
    private final PasswordGenerator generator;

    @Override
    public void run(String... args) throws IOException {
        SystemUserRequestDto user1 = new SystemUserRequestDto(
                "ABC", "XYZ", "algox1234@gmail.com", "12345678", "0714911257");
        SystemUserRequestDto user2 = new SystemUserRequestDto(
                "Asitha", "Pathirathna ", "asithahpathirathne@gmail.com",
                generator.generatePassword(), "0714875865");
        SystemUserRequestDto admin = new SystemUserRequestDto(
                "Amiru", "Horadagoda", "eventhub.admin@local.test", "amiru1234", "0717244872");

        service.initializeHosts(List.of(user1, user2));
        service.initializeAdmins(List.of(admin));
    }
}
