package com.ec7205.event_hub.auth_service_api.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostPasswordNotificationRequest {
    private String email;
    private String password;
    private String firstName;
}
