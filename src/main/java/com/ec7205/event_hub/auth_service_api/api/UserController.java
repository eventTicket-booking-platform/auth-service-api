package com.ec7205.event_hub.auth_service_api.api;

import com.ec7205.event_hub.auth_service_api.config.JwtService;
import com.ec7205.event_hub.auth_service_api.dto.request.PasswordRequestDto;
import com.ec7205.event_hub.auth_service_api.dto.request.RequestLoginDto;
import com.ec7205.event_hub.auth_service_api.dto.request.SystemUserRequestDto;
import com.ec7205.event_hub.auth_service_api.dto.request.UpdateUserRequestDto;
import com.ec7205.event_hub.auth_service_api.dto.response.ResponseUserDetailsDto;
import com.ec7205.event_hub.auth_service_api.service.SystemUserService;
import com.ec7205.event_hub.auth_service_api.utils.StandardResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Controller
@RequestMapping("/user-service/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final SystemUserService systemUserService;
    private final JwtService jwtService;

    @PostMapping("/visitors/signup")
    public ResponseEntity<StandardResponseDto> createUser(@RequestBody SystemUserRequestDto dto) throws IOException {
        systemUserService.createUser(dto);
        return new ResponseEntity<>(
                new StandardResponseDto(201, "user account was created!", null),
                HttpStatus.CREATED
        );
    }

    @PostMapping("/visitors/resend")
    public ResponseEntity<StandardResponseDto> resend(@RequestParam String email, @RequestParam String type) throws IOException {
        systemUserService.resend(email, type);
        return new ResponseEntity<>(
                new StandardResponseDto(200, "please check your email", null),
                HttpStatus.OK
        );
    }

    @PostMapping("/visitors/forget-password-request-code")
    public ResponseEntity<StandardResponseDto> forgetPasswordRequest(@RequestParam String email) throws IOException {
        systemUserService.forgetPasswordSendVerificationCode(email);
        return new ResponseEntity<>(
                new StandardResponseDto(200, "please check your email", null),
                HttpStatus.OK
        );
    }

    //call when we need to change the password of the account
    @PostMapping("/visitors/verify-reset")
    public ResponseEntity<StandardResponseDto> verifyReset(
            @RequestParam String email,
            @RequestParam String otp
    ) throws IOException {
        boolean isVerified = systemUserService.verifyReset(otp, email);
        return new ResponseEntity<>(
                new StandardResponseDto(isVerified ? 200 : 400, isVerified ? "Verified" : "try Again", isVerified),
                isVerified ? HttpStatus.OK : HttpStatus.BAD_REQUEST
        );
    }

    @PostMapping("/visitors/reset-password")
    public ResponseEntity<StandardResponseDto> resetPassword(
            @RequestBody PasswordRequestDto dto
    ) throws IOException {

        boolean isChanged = systemUserService.passwordReset(dto);
        return new ResponseEntity<>(
                new StandardResponseDto(isChanged ? 201 : 400, isChanged ? "CHANGED" : "try Again", isChanged),
                isChanged ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST
        );
    }

    @PostMapping("/visitors/verify-email")
    public ResponseEntity<StandardResponseDto> verifyEmail(
            @RequestParam String email,
            @RequestParam String otp
    ) throws IOException {

        boolean isVerified = systemUserService.verifyEmail(otp, email);
        return new ResponseEntity<>(
                new StandardResponseDto(isVerified ? 200 : 400, isVerified ? "Verified" : "try Again", isVerified),
                isVerified ? HttpStatus.OK : HttpStatus.BAD_REQUEST
        );
    }

    @PostMapping("/visitors/login")
    public ResponseEntity<StandardResponseDto> login(
            @RequestBody RequestLoginDto dto
    ) throws IOException {

        return new ResponseEntity<>(
                new StandardResponseDto(200, "success", systemUserService.userLogin(dto)),
                HttpStatus.OK
        );
    }

    //create this api to get the details of the login user
    @GetMapping("/get-user-details")
    @PreAuthorize("hasAnyRole('user','admin')")
    public ResponseEntity<StandardResponseDto> getUserDetails(@RequestHeader("Authorization") String tokenHeader) {
        String token = tokenHeader.replace("Bearer ", "");
        String email = jwtService.getEmail(token);

        ResponseUserDetailsDto userDetails = systemUserService.getUserDetails(email);

        return new ResponseEntity<>(
                new StandardResponseDto(200, "user details!", userDetails), HttpStatus.OK
        );
    }

    @PutMapping("/update-user-details")
    @PreAuthorize("hasAnyRole('user','admin')")
    public ResponseEntity<StandardResponseDto> updateUserDetails(@RequestHeader("Authorization") String tokenHeader,
                                                                 @RequestBody UpdateUserRequestDto updateUserRequestDto) {
        String token = tokenHeader.replace("Bearer ", "");
        String email = jwtService.getEmail(token);

        systemUserService.updateUserDetails(email, updateUserRequestDto);

        return new ResponseEntity<>(
                new StandardResponseDto(200, "user details updated!", null), HttpStatus.OK
        );
    }


}
