package com.ec7205.event_hub.auth_service_api.api;

import com.ec7205.event_hub.auth_service_api.dto.request.HostApplicationRejectionRequestDto;
import com.ec7205.event_hub.auth_service_api.entity.HostApplicationStatus;
import com.ec7205.event_hub.auth_service_api.service.AdminHostApplicationService;
import com.ec7205.event_hub.auth_service_api.utils.StandardResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user-service/api/v1/host-applications/admin")
@PreAuthorize("hasAuthority('admin')")
@RequiredArgsConstructor
public class AdminHostApplicationController {
    private final AdminHostApplicationService applications;

    @GetMapping
    public ResponseEntity<StandardResponseDto> list(
            @RequestParam(required = false) HostApplicationStatus status,
            @PageableDefault(size = 20, sort = {"appliedAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(new StandardResponseDto(200, "Host applications", applications.list(status, pageable)));
    }

    @PatchMapping("/{applicationId}/approve")
    public ResponseEntity<StandardResponseDto> approve(@PathVariable String applicationId,
                                                      @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(new StandardResponseDto(200, "Host application approved",
                applications.approve(applicationId, jwt.getSubject())));
    }

    @PatchMapping("/{applicationId}/reject")
    public ResponseEntity<StandardResponseDto> reject(@PathVariable String applicationId,
                                                     @AuthenticationPrincipal Jwt jwt,
                                                     @RequestBody HostApplicationRejectionRequestDto request) {
        return ResponseEntity.ok(new StandardResponseDto(200, "Host application rejected",
                applications.reject(applicationId, jwt.getSubject(), request.getReason())));
    }
}
