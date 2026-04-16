package com.ec7205.event_hub.auth_service_api.service.impl;

import com.ec7205.event_hub.auth_service_api.client.dto.EmailVerificationOtpNotificationRequest;
import com.ec7205.event_hub.auth_service_api.client.dto.HostPasswordNotificationRequest;
import com.ec7205.event_hub.auth_service_api.client.dto.PasswordResetOtpNotificationRequest;
import com.ec7205.event_hub.auth_service_api.config.KeycloakSecurityUtil;
import com.ec7205.event_hub.auth_service_api.dto.request.RequestLoginDto;
import com.ec7205.event_hub.auth_service_api.dto.request.PasswordRequestDto;
import com.ec7205.event_hub.auth_service_api.dto.request.RequestRefreshTokenDto;
import com.ec7205.event_hub.auth_service_api.dto.request.SystemUserRequestDto;
import com.ec7205.event_hub.auth_service_api.dto.request.UpdateUserRequestDto;
import com.ec7205.event_hub.auth_service_api.dto.response.ResponseUserDetailsDto;
import com.ec7205.event_hub.auth_service_api.dto.response.pagination.UserDetailsPaginateResponseDto;
import com.ec7205.event_hub.auth_service_api.entity.Otp;
import com.ec7205.event_hub.auth_service_api.entity.SystemAvatar;
import com.ec7205.event_hub.auth_service_api.entity.SystemUser;
import com.ec7205.event_hub.auth_service_api.exceptions.BadRequestException;
import com.ec7205.event_hub.auth_service_api.exceptions.DuplicateEntryException;
import com.ec7205.event_hub.auth_service_api.exceptions.EntryNotFoundException;
import com.ec7205.event_hub.auth_service_api.exceptions.UnauthorizedException;
import com.ec7205.event_hub.auth_service_api.repo.OtpRepo;
import com.ec7205.event_hub.auth_service_api.repo.SystemUserRepo;
import com.ec7205.event_hub.auth_service_api.service.SystemUserService;
import com.ec7205.event_hub.auth_service_api.messaging.AuthNotificationEventPublisher;
import com.ec7205.event_hub.auth_service_api.messaging.dto.AuthNotificationEvent;
import com.ec7205.event_hub.auth_service_api.utils.FileDataExtractor;
import com.ec7205.event_hub.auth_service_api.utils.OtpGenerator;


import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SystemUserServiceImpl implements SystemUserService {
    private static final List<String> BUSINESS_ROLES = List.of("ADMIN", "HOST", "USER");

    @Value("${keycloak.config.realm}")
    private String realm; // The Keycloak realm name (from application properties)

    @Value("${spring.security.oauth2.resourceserver.jwt.token-uri}")
    private String keyClockApiUrl;

    @Value("${keycloak.config.client-id}")
    private String clientId;

    @Value("${keycloak.config.secret}")
    private String secret;


    private final SystemUserRepo systemUserRepo; // Repository for managing SystemUser table
    private final KeycloakSecurityUtil keyClockUtil; // Utility to get Keycloak admin client instance
    private final OtpRepo otpRepo; // Repository for managing OTP table
    private final OtpGenerator otpGenerator; // Utility for generating OTP codes
//    private final EmailService emailService; // Service for sending emails
    private final AuthNotificationEventPublisher notificationEventPublisher;
    private final FileDataExtractor fileDataExtractor;

    @Override
    public void createUser(SystemUserRequestDto dto) throws IOException {
        // --- 1. Validate required fields in the request DTO ---
        if (dto.getFirstName() == null || dto.getFirstName().trim().isEmpty()) {
            throw new BadRequestException("First name is required");
        }
        if (dto.getLastName() == null || dto.getLastName().trim().isEmpty()) {
            throw new BadRequestException("Last name is required");
        }
        if (dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
            throw new BadRequestException("First name is required");
        }

        String userId = "";
        String otp = "";
        Keycloak keycloak = null;

        UserRepresentation existingUser = null;
        keycloak = keyClockUtil.getKeycloakInstance(); // Get Keycloak admin client instance

        // --- 2. Check if the user already exists in Keycloak ---
        existingUser = keycloak.realm(realm)
                .users()
                .search(dto.getEmail())
                .stream().findFirst().orElse(null);

        /**
         * Business rule:
         * - A valid user record must be present in BOTH SystemUser (local DB) and Keycloak.
         * - If present in only one, delete it from that source.
         * - If present in neither, create it in both.
         */

        // --- 3. If user exists in Keycloak ---
        if (existingUser != null) {
            // Check if it exists in local SystemUser DB
            Optional<SystemUser> selectedSystemUserFromAuthService =
                    systemUserRepo.findByEmail(dto.getEmail());

            if (selectedSystemUserFromAuthService.isEmpty()) {
                // If exists only in Keycloak but NOT in local DB → delete from Keycloak
                keycloak.realm(realm).users().delete(existingUser.getId());
            } else {
                // If exists in both → throw duplicate entry error
                throw new DuplicateEntryException("Email already exists");
            }
        } else {
            // --- 4. If user does NOT exist in Keycloak ---
            Optional<SystemUser> selectedSystemUserFromAuthService =
                    systemUserRepo.findByEmail(dto.getEmail());

            if (selectedSystemUserFromAuthService.isPresent()) {
                // If exists only in local DB → delete any associated OTP and remove user from local DB
                Optional<Otp> selectedOtp =
                        otpRepo.findBySystemUserId(selectedSystemUserFromAuthService.get().getUserId());

                if (selectedOtp.isPresent()) {
                    otpRepo.deleteById(selectedOtp.get().getPropertyId());
                }
                systemUserRepo.deleteById(selectedSystemUserFromAuthService.get().getUserId());
            }
        }

        // --- 5. Map SystemUserRequestDto → Keycloak's UserRepresentation ---
        UserRepresentation userRepresentation = mapUserRepo(dto, false, false);

        // --- 6. Create user in Keycloak ---
        Response response = keycloak.realm(realm).users().create(userRepresentation);

        // --- 7. If creation successful (HTTP 201) ---
        if (response.getStatus() == Response.Status.CREATED.getStatusCode()) {
            // Assign "user" role in Keycloak
            RoleRepresentation userRole = keycloak.realm(realm).roles().get("user").toRepresentation();
            userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            keycloak.realm(realm).users().get(userId).roles().realmLevel().add(Arrays.asList(userRole));

            // Fetch the created user details from Keycloak
            UserRepresentation createdUser = keycloak.realm(realm).users().get(userId).toRepresentation();

            // --- 8. Save user record in local SystemUser table ---
            SystemUser sUser = SystemUser.builder()
                    .userId(userId)
                    .keycloakId(createdUser.getId())
                    .firstName(dto.getFirstName())
                    .lastName(dto.getLastName())
                    .email(dto.getEmail())
                    .contact(dto.getContact())
                    .isActive(false)
                    .isAccountNonExpired(true)
                    .isAccountNonLocked(true)
                    .isCredentialsNonExpired(true)
                    .isEnabled(false)
                    .isEmailVerified(false)
                    .createdAt(new Date().toInstant())
                    .updatedAt(new Date().toInstant())
                    .build();

            SystemUser savedUser = systemUserRepo.save(sUser);

            // --- 9. Create OTP for verification and save in OTP table ---
            Otp createdOtp = Otp.builder()
                    .propertyId(UUID.randomUUID().toString())
                    .code(otpGenerator.generateOtp(5))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .isVerified(false)
                    .systemUser(savedUser)
                    .attempts(0)
                    .build();
            otpRepo.save(createdOtp);

            // --- 10. Send verification email with OTP ---
            publishAuthNotification("EMAIL_VERIFICATION_OTP", EmailVerificationOtpNotificationRequest.builder()
                    .userId(userId)
                    .email(dto.getEmail())
                    .name(dto.getFirstName())
                    .otp(createdOtp.getCode())
                    .build());
        }
    }

    // --- Helper method: Map DTO to Keycloak UserRepresentation ---
    private UserRepresentation mapUserRepo(SystemUserRequestDto dto, boolean isEmailVerified, boolean isEnabled) {
        UserRepresentation user = new UserRepresentation();
        user.setEmail(dto.getEmail());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setUsername(dto.getEmail());
        user.setEnabled(isEnabled);
        user.setEmailVerified(isEmailVerified);

        // Create password credentials
        List<CredentialRepresentation> credList = new ArrayList<>();
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setTemporary(false);
        cred.setValue(dto.getPassword());
        credList.add(cred);

        user.setCredentials(credList);
        return user;
    }

    // --- Method to initially add users with role "Host" ---
    @Override
    public void initializeHosts(List<SystemUserRequestDto> users) throws IOException {
        for (SystemUserRequestDto dto : users) {
            // If already exists in local DB → skip
            Optional<SystemUser> selectedUser = systemUserRepo.findByEmail(dto.getEmail());
            if (selectedUser.isPresent()) {
                continue;
            }

            String userId = "";
            String otp = "";
            Keycloak keycloak = null;
            UserRepresentation existingUser = null;
            keycloak = keyClockUtil.getKeycloakInstance();

            // Check if exists in Keycloak
            existingUser = keycloak.realm(realm).users().search(dto.getEmail()).stream()
                    .findFirst().orElse(null);

            if (existingUser != null) {
                Optional<SystemUser> selectedSystemUserFromAuthService =
                        systemUserRepo.findByEmail(dto.getEmail());
                if (selectedSystemUserFromAuthService.isEmpty()) {
                    keycloak.realm(realm).users().delete(existingUser.getId());
                } else {
                    throw new DuplicateEntryException("Email already exists");
                }
            } else {
                Optional<SystemUser> selectedSystemUserFromAuthService =
                        systemUserRepo.findByEmail(dto.getEmail());
                if (selectedSystemUserFromAuthService.isPresent()) {
                    Optional<Otp> selectedOtp =
                            otpRepo.findBySystemUserId(selectedSystemUserFromAuthService.get().getUserId());
                    if (selectedOtp.isPresent()) {
                        otpRepo.deleteById(selectedOtp.get().getPropertyId());
                    }
                    systemUserRepo.deleteById(selectedSystemUserFromAuthService.get().getUserId());
                }
            }

            // Create Keycloak user with email verified and enabled (since these are initial users)
            UserRepresentation userRepresentation = mapUserRepo(dto, true, true);
            Response response = keycloak.realm(realm).users().create(userRepresentation);

            if (response.getStatus() == Response.Status.CREATED.getStatusCode()) {
                // Assign "host" role
                RoleRepresentation userRole = keycloak.realm(realm).roles().get("host").toRepresentation();
                userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
                keycloak.realm(realm).users().get(userId).roles().realmLevel().add(Arrays.asList(userRole));

                // Save user in local DB
                UserRepresentation createdUser = keycloak.realm(realm).users().get(userId).toRepresentation();
                SystemUser sUser = SystemUser.builder()
                        .userId(userId)
                        .keycloakId(createdUser.getId())
                        .firstName(dto.getFirstName())
                        .lastName(dto.getLastName())
                        .email(dto.getEmail())
                        .contact(dto.getContact())
                        .isActive(true)
                        .isAccountNonExpired(true)
                        .isAccountNonLocked(true)
                        .isCredentialsNonExpired(true)
                        .isEnabled(true)
                        .isEmailVerified(true)
                        .createdAt(new Date().toInstant())
                        .updatedAt(new Date().toInstant())
                        .build();

                SystemUser savedUser = systemUserRepo.save(sUser);

                // Send password email to host users
                publishAuthNotification("HOST_PASSWORD", HostPasswordNotificationRequest.builder()
                        .email(dto.getEmail())
                        .firstName(dto.getFirstName())
                        .password(dto.getPassword())
                        .build());
            }
        }
    }

    @Override
    public void resend(String email, String type) {
        try {
            Optional<SystemUser> selectedUser = systemUserRepo.findByEmail(email);
            if (selectedUser.isEmpty()) {
                throw new EntryNotFoundException("unable to find any users associated withe the provided email");
            }
            SystemUser systemUser = selectedUser.get();
            if (type.equalsIgnoreCase("SIGNUP")) {
                if (systemUser.isEmailVerified()) {
                    throw new DuplicateEntryException("The email is already activated");
                }
            }
            Otp selectedOtpObj = systemUser.getOtp();

            String code = otpGenerator.generateOtp(5);
            publishAuthNotification("EMAIL_VERIFICATION_OTP", EmailVerificationOtpNotificationRequest.builder()
                    .userId(systemUser.getUserId())
                    .email(systemUser.getEmail())
                    .name(systemUser.getFirstName())
                    .otp(code)
                    .build());
            selectedOtpObj.setAttempts(0);
            selectedOtpObj.setCode(code);
            selectedOtpObj.setIsVerified(false);
            selectedOtpObj.setUpdatedAt(new Date().toInstant());
            otpRepo.save(selectedOtpObj);

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void forgetPasswordSendVerificationCode(String email) {
        try {
            Optional<SystemUser> selectedUser = systemUserRepo.findByEmail(email);
            if (selectedUser.isEmpty()) {
                throw new EntryNotFoundException("unable to find any users associated withe the provided email");
            }
            SystemUser systemUser = selectedUser.get();
            Keycloak keycloak = null;
            keycloak = keyClockUtil.getKeycloakInstance();
            UserRepresentation existingUser = keycloak.realm(realm).users().search(email).stream().findFirst().orElse(null);
            if (existingUser == null) {
                throw new EntryNotFoundException("Unable to find any users associated with the provided email address");
            }

            Otp selectedOtpObj = systemUser.getOtp();

            String code = otpGenerator.generateOtp(5);
            selectedOtpObj.setAttempts(0);
            selectedOtpObj.setCode(code);
            selectedOtpObj.setIsVerified(false);
            selectedOtpObj.setUpdatedAt(new Date().toInstant());
            otpRepo.save(selectedOtpObj);
            publishAuthNotification("PASSWORD_RESET_OTP", PasswordResetOtpNotificationRequest.builder()
                    .userId(systemUser.getUserId())
                    .email(systemUser.getEmail())
                    .name(systemUser.getFirstName())
                    .otp(code)
                    .build());

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public boolean verifyReset(String otp, String email) {
        try {
            Optional<SystemUser> selectedUser = systemUserRepo.findByEmail(email);
            if (selectedUser.isEmpty()) {
                throw new EntryNotFoundException("unable to find any users associated withe the provided email");
            }
            SystemUser systemUser = selectedUser.get();
            Otp otpObj = systemUser.getOtp();
            if (otpObj.getCode().equals(otp)) {
                otpRepo.deleteById(otpObj.getPropertyId());
                return true;
            } else {
                if (otpObj.getAttempts() >= 5) {
                    resend(email, "PASSWORD");
                    throw new BadRequestException("You have a new Verification code");
                }
                otpObj.setAttempts(otpObj.getAttempts() + 1);
                otpObj.setUpdatedAt(new Date().toInstant());
                otpObj.setIsVerified(true);
                otpRepo.save(otpObj);
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean passwordReset(PasswordRequestDto dto) {

        Optional<SystemUser> selectedUserObj = systemUserRepo.findByEmail(dto.getEmail());
        if (selectedUserObj.isPresent()) {
            SystemUser systemUser = selectedUserObj.get();
            Otp otpObj = systemUser.getOtp();
            Keycloak keycloak = keyClockUtil.getKeycloakInstance();
            List<UserRepresentation> keyclockUsers = keycloak.realm(realm).users().search(systemUser.getEmail());
            if (!keyclockUsers.isEmpty() && otpObj.getCode().equals(dto.getCode())) {
                UserRepresentation keyclockUser = keyclockUsers.get(0);
                UserResource userResource = keycloak.realm(realm).users().get(keyclockUser.getId());
                CredentialRepresentation newPass = new CredentialRepresentation();
                newPass.setType(CredentialRepresentation.PASSWORD);
                newPass.setValue(dto.getPassword());
                newPass.setTemporary(false);
                userResource.resetPassword(newPass);

                systemUser.setUpdatedAt(new Date().toInstant());
                systemUserRepo.save(systemUser);
                return true;
            }
            throw new BadRequestException("Try again!");
        }
        throw new EntryNotFoundException("Unable to find!");

    }

    @Override
    public boolean verifyEmail(String otp, String email) {
        Optional<SystemUser> selectedUserObj = systemUserRepo.findByEmail(email);
        if (selectedUserObj.isEmpty()) {
            throw new EntryNotFoundException("Cant find the associated user");
        }
        SystemUser systemUser = selectedUserObj.get();
        Otp otpObj = systemUser.getOtp();
        if (otpObj.getIsVerified()) {
            throw new BadRequestException("This otp has been used!");
        }
        if (otpObj.getAttempts() >= 5) {
            resend(email, "SIGNUP");
            return false;
        }

        if (otpObj.getCode().equals(otp)) {
            UserRepresentation keyclockUser = keyClockUtil.getKeycloakInstance()
                    .realm(realm)
                    .users()
                    .search(email)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new EntryNotFoundException("User not found"));
            keyclockUser.setEmailVerified(true);
            keyclockUser.setEnabled(true);

            keyClockUtil.getKeycloakInstance()
                    .realm(realm)
                    .users()
                    .get(keyclockUser.getId())
                    .update(keyclockUser);

            systemUser.setEmailVerified(true);
            systemUser.setEnabled(true);
            systemUser.setActive(true);

            systemUserRepo.save(systemUser);

            otpObj.setIsVerified(true);
            otpObj.setAttempts(otpObj.getAttempts() + 1);
            otpRepo.save(otpObj);

            return true;

        } else {
            if (otpObj.getAttempts() >= 5) {
                resend(email, "SIGNUP");
                return false;
            }
            otpObj.setAttempts(otpObj.getAttempts() + 1);
            otpRepo.save(otpObj);
        }
        return false;
    }

    @Override
    public Object userLogin(RequestLoginDto dto) {
        Optional<SystemUser> selectedUserObj = systemUserRepo.findByEmail(dto.getEmail());

        if (selectedUserObj.isEmpty()) {
            throw new EntryNotFoundException("Cant find the associated user");
        }
        SystemUser systemUser = selectedUserObj.get();
        if (!systemUser.isEmailVerified()) {
            resend(dto.getEmail(), "SIGNUP");
            throw new UnauthorizedException("Please verify email");
        }
        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("client_id", clientId);
        requestBody.add("grant_type", OAuth2Constants.PASSWORD);
        requestBody.add("username", dto.getEmail());
        requestBody.add("client_secret", secret);
        requestBody.add("password", dto.getPassword());
        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Object> response = restTemplate.postForEntity(keyClockApiUrl, requestBody, Object.class);
        return response.getBody();
    }

    @Override
    public Object refreshAccessToken(RequestRefreshTokenDto dto) {
        if (dto == null || dto.getRefreshToken() == null || dto.getRefreshToken().isBlank()) {
            throw new BadRequestException("refresh token is required");
        }

        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("client_id", clientId);
        requestBody.add("grant_type", OAuth2Constants.REFRESH_TOKEN);
        requestBody.add("client_secret", secret);
        requestBody.add("refresh_token", dto.getRefreshToken());

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Object> response = restTemplate.postForEntity(keyClockApiUrl, requestBody, Object.class);
        return response.getBody();
    }

    @Override
    public ResponseUserDetailsDto getUserDetails(String email) {
        Optional<SystemUser> byEmail = systemUserRepo.findByEmail(email);
        if (byEmail.isEmpty()) {
            throw new EntryNotFoundException("User was not found!");
        }
        return mapToUserDetailsResponse(byEmail.get());
    }

    @Override
    public String resolveUserIdByEmail(String email) {
        Optional<SystemUser> byEmail = systemUserRepo.findByEmail(email);
        if (byEmail.isEmpty()) {
            throw new EntryNotFoundException("User was not found!");
        }
        return byEmail.get().getUserId();
    }

    @Override
    public UserDetailsPaginateResponseDto getAllUsers(Pageable pageable) {
        Page<ResponseUserDetailsDto> userPage = systemUserRepo.findAll(pageable)
                .map(this::mapToUserDetailsResponse);

        return UserDetailsPaginateResponseDto.builder()
                .dataList(userPage.getContent())
                .dataCount(userPage.getTotalElements())
                .build();
    }

    @Override
    public void updateUserDetails(String email, UpdateUserRequestDto updateUserRequestDto) {
        Optional<SystemUser> byEmail = systemUserRepo.findByEmail(email);
        if (byEmail.isEmpty()) {
            throw new EntryNotFoundException("User was not found!");
        }
        byEmail.get().setFirstName(updateUserRequestDto.getFirstName());
        byEmail.get().setLastName(updateUserRequestDto.getLastName());

        systemUserRepo.save(byEmail.get());
    }

    private ResponseUserDetailsDto mapToUserDetailsResponse(SystemUser systemUser) {
        SystemAvatar systemUserAvatar = systemUser.getSystemAvatar();
        return ResponseUserDetailsDto.builder()
                .userId(systemUser.getUserId())
                .email(systemUser.getEmail())
                .firstName(systemUser.getFirstName())
                .lastName(systemUser.getLastName())
                .role(resolveUserRole(systemUser))
                .resourceUrl(systemUserAvatar != null ? fileDataExtractor.byteArrayToString(systemUserAvatar.getResourceUrl()) : null)
                .build();
    }

    private String resolveUserRole(SystemUser systemUser) {
        if (systemUser.getKeycloakId() == null || systemUser.getKeycloakId().isBlank()) {
            return "UNKNOWN";
        }

        try {
            List<RoleRepresentation> roles = keyClockUtil.getKeycloakInstance()
                    .realm(realm)
                    .users()
                    .get(systemUser.getKeycloakId())
                    .roles()
                    .realmLevel()
                    .listAll();

            return roles.stream()
                    .map(RoleRepresentation::getName)
                    .filter(Objects::nonNull)
                    .map(String::toUpperCase)
                    .filter(BUSINESS_ROLES::contains)
                    .findFirst()
                    .orElse("UNKNOWN");
        } catch (Exception ex) {
            return "UNKNOWN";
        }
    }

    private void publishAuthNotification(String type, Object payload) {
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        if (payload instanceof EmailVerificationOtpNotificationRequest request) {
            payloadMap.put("userId", request.getUserId());
            payloadMap.put("email", request.getEmail());
            payloadMap.put("name", request.getName());
            payloadMap.put("otp", request.getOtp());
        } else if (payload instanceof PasswordResetOtpNotificationRequest request) {
            payloadMap.put("userId", request.getUserId());
            payloadMap.put("email", request.getEmail());
            payloadMap.put("name", request.getName());
            payloadMap.put("otp", request.getOtp());
        } else if (payload instanceof HostPasswordNotificationRequest request) {
            payloadMap.put("email", request.getEmail());
            payloadMap.put("password", request.getPassword());
            payloadMap.put("firstName", request.getFirstName());
        } else {
            throw new BadRequestException("Unsupported notification payload");
        }

        notificationEventPublisher.publish(AuthNotificationEvent.builder()
                .type(type)
                .payload(payloadMap)
                .build());
    }
}
