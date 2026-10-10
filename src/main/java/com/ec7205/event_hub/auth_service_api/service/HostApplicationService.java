package com.ec7205.event_hub.auth_service_api.service;

import com.ec7205.event_hub.auth_service_api.dto.request.HostApplicationRequestDto;
import com.ec7205.event_hub.auth_service_api.dto.response.HostApplicationResponseDto;

public interface HostApplicationService {
    HostApplicationResponseDto apply(String authenticatedEmail, HostApplicationRequestDto request);
    HostApplicationResponseDto getCurrent(String authenticatedEmail);
}
