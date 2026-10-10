package com.ec7205.event_hub.auth_service_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class AuthServiceApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApiApplication.class, args);
    }
}
