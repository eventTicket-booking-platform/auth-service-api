package com.ec7205.event_hub.auth_service_api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HostApplicationRequestDto {
    private String organizationName;
    private String reason;
}
