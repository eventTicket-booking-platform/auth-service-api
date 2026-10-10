package com.ec7205.event_hub.auth_service_api.service;

import com.ec7205.event_hub.auth_service_api.dto.response.AdminHostApplicationResponseDto;
import com.ec7205.event_hub.auth_service_api.dto.response.pagination.HostApplicationPageResponseDto;
import com.ec7205.event_hub.auth_service_api.entity.HostApplicationStatus;
import org.springframework.data.domain.Pageable;

public interface AdminHostApplicationService {
    HostApplicationPageResponseDto list(HostApplicationStatus status, Pageable pageable);
    AdminHostApplicationResponseDto approve(String applicationId, String adminId);
    AdminHostApplicationResponseDto reject(String applicationId, String adminId, String reason);
}
