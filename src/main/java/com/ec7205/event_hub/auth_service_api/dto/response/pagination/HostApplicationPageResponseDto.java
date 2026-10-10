package com.ec7205.event_hub.auth_service_api.dto.response.pagination;

import com.ec7205.event_hub.auth_service_api.dto.response.AdminHostApplicationResponseDto;

import java.util.List;

public record HostApplicationPageResponseDto(List<AdminHostApplicationResponseDto> dataList, long dataCount) {
}
