package com.ec7205.event_hub.auth_service_api.api;

import com.ec7205.event_hub.auth_service_api.dto.request.HostApplicationRequestDto;
import com.ec7205.event_hub.auth_service_api.service.HostApplicationService;
import com.ec7205.event_hub.auth_service_api.utils.StandardResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user-service/api/v1/host-applications")
@PreAuthorize("hasAnyAuthority('user','customer') and !hasAnyAuthority('host','admin')")
@RequiredArgsConstructor
public class HostApplicationController {
    private final HostApplicationService applications;

    @PostMapping
    public ResponseEntity<StandardResponseDto> apply(@AuthenticationPrincipal Jwt jwt,
                                                    @RequestBody HostApplicationRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new StandardResponseDto(
                201, "Host application submitted", applications.apply(jwt.getClaimAsString("email"), request)));
    }

    @GetMapping("/me")
    public ResponseEntity<StandardResponseDto> getCurrent(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(new StandardResponseDto(
                200, "Host application", applications.getCurrent(jwt.getClaimAsString("email"))));
    }
}
