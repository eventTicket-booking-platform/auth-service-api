package com.ec7205.event_hub.auth_service_api;

import com.ec7205.event_hub.auth_service_api.dto.request.SystemUserRequestDto;
import com.ec7205.event_hub.auth_service_api.service.SystemUserService;
import com.ec7205.event_hub.auth_service_api.utils.PasswordGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.Arrays;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@RequiredArgsConstructor
public class AuthServiceApiApplication implements CommandLineRunner {

	private final SystemUserService service;
	private final PasswordGenerator generator;

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApiApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {

		SystemUserRequestDto user1= new SystemUserRequestDto("ABC","XYZ","algox1234@gmail.com",generator.generatePassword(),"0714911257");
		SystemUserRequestDto user2= new SystemUserRequestDto("Asitha","Pathirathna ","asithahpathirathne@gmail.com",generator.generatePassword(),"0714875865");


		service.initializeHosts(Arrays.asList(user1, user2));
	}

}
