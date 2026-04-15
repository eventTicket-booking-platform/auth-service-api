package com.ec7205.event_hub.auth_service_api.client;

import com.ec7205.event_hub.auth_service_api.client.dto.EmailVerificationOtpNotificationRequest;
import com.ec7205.event_hub.auth_service_api.client.dto.HostPasswordNotificationRequest;
import com.ec7205.event_hub.auth_service_api.client.dto.PasswordResetOtpNotificationRequest;
import com.ec7205.event_hub.auth_service_api.utils.StandardResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service-api", url = "http://localhost:9094")
public interface NotificationServiceClient {

    @PostMapping("/notification-service/api/v1/internal/notifications/email-verification-otp")
    StandardResponseDto sendEmailVerificationOtp(
            @RequestBody EmailVerificationOtpNotificationRequest request
    );

    @PostMapping("/notification-service/api/v1/internal/notifications/password-reset-otp")
    StandardResponseDto sendPasswordResetOtp(
            @RequestBody PasswordResetOtpNotificationRequest request
    );

    @PostMapping("/notification-service/api/v1/admin/notifications/send-host-password")
    StandardResponseDto sendHostPassword(
            @RequestBody HostPasswordNotificationRequest request
    );
}
