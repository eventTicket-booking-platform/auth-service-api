package com.ec7205.event_hub.auth_service_api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import com.ec7205.event_hub.auth_service_api.service.SystemUserService;
import com.ec7205.event_hub.auth_service_api.utils.PasswordGenerator;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceApiApplicationTests {

	@Autowired
	private AuthServiceApiApplication application;

	@MockBean
	private SystemUserService systemUserService;

	@MockBean
	private PasswordGenerator passwordGenerator;

	@Test
	void contextLoads() {
		// Verifies the application context starts with test-safe infrastructure settings.
	}

}
