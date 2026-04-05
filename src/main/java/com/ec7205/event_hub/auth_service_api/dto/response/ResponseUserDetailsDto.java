package com.ec7205.event_hub.auth_service_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResponseUserDetailsDto {
    private String email;
    private String firstName;
    private String lastName;
    private String resourceUrl;
}
