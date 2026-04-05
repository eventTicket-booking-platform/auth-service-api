package com.ec7205.event_hub.auth_service_api.service;

import com.ec7205.event_hub.auth_service_api.dto.request.PasswordRequestDto;
import com.ec7205.event_hub.auth_service_api.dto.request.RequestLoginDto;
import com.ec7205.event_hub.auth_service_api.dto.request.SystemUserRequestDto;
import com.ec7205.event_hub.auth_service_api.dto.request.UpdateUserRequestDto;
import com.ec7205.event_hub.auth_service_api.dto.response.ResponseUserDetailsDto;

import java.io.IOException;
import java.util.List;

public interface SystemUserService {
    public void createUser(SystemUserRequestDto dto) throws IOException;
    public void initializeHosts(List<SystemUserRequestDto> users) throws IOException;
    public void resend(String email,String type) throws IOException;
    public void forgetPasswordSendVerificationCode(String email );
    public boolean verifyReset(String otp, String email);
    public boolean passwordReset(PasswordRequestDto passwordRequestDto);
    public boolean verifyEmail(String otp,String email);
    public Object userLogin(RequestLoginDto dto);
    public ResponseUserDetailsDto getUserDetails(String email);
    public void updateUserDetails(String email, UpdateUserRequestDto updateUserRequestDto);
}
