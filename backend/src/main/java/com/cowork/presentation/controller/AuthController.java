package com.cowork.presentation.controller;

import com.cowork.business.service.AuthService;
import com.cowork.business.service.OtpService;
import com.cowork.dto.AuthDtos.AuthResponse;
import com.cowork.dto.AuthDtos.ChangePasswordRequest;
import com.cowork.dto.AuthDtos.LoginRequest;
import com.cowork.dto.AuthDtos.OtpLoginRequest;
import com.cowork.dto.AuthDtos.OtpRequest;
import com.cowork.dto.AuthDtos.OtpSentResponse;
import com.cowork.dto.AuthDtos.ProfileResponse;
import com.cowork.dto.AuthDtos.RegisterEmailRequest;
import com.cowork.dto.AuthDtos.RegisterPhoneRequest;
import com.cowork.dto.AuthDtos.ResetPasswordRequest;
import com.cowork.dto.AuthDtos.StaffLoginRequest;
import com.cowork.dto.AuthDtos.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** UC01: khách đăng ký / đăng nhập. UC23: nhân viên đăng nhập. Mọi endpoint ở đây công khai, trừ {@code /me}. */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;
    private final OtpService otpService;

    AuthController(AuthService authService, OtpService otpService) {
        this.authService = authService;
        this.otpService = otpService;
    }

    @PostMapping("/otp")
    OtpSentResponse sendOtp(@Valid @RequestBody OtpRequest request) {
        return new OtpSentResponse(request.phone(), otpService.send(request.phone()));
    }

    @PostMapping("/register/phone")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse registerByPhone(@Valid @RequestBody RegisterPhoneRequest request) {
        return authService.registerByPhone(request);
    }

    @PostMapping("/register/email")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse registerByEmail(@Valid @RequestBody RegisterEmailRequest request) {
        return authService.registerByEmail(request);
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/login/otp")
    AuthResponse loginWithOtp(@Valid @RequestBody OtpLoginRequest request) {
        return authService.loginWithOtp(request);
    }

    @PostMapping("/staff/login")
    AuthResponse staffLogin(@Valid @RequestBody StaffLoginRequest request) {
        return authService.staffLogin(request);
    }

    /** Hồ sơ của người đang đăng nhập (khách hoặc nhân viên); khách có thêm số phút gói thành viên còn lại. */
    @GetMapping("/me")
    ProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.profile(Roles.actorOf(jwt));
    }

    /** FR-AUTH-05: khách sửa hồ sơ. */
    @PutMapping("/me")
    @PreAuthorize(Roles.CUSTOMER)
    ProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request, @AuthenticationPrincipal Jwt jwt) {
        return authService.updateProfile(Roles.actorOf(jwt), request);
    }

    /** FR-AUTH-05: khách đổi mật khẩu. */
    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.CUSTOMER)
    void changePassword(@Valid @RequestBody ChangePasswordRequest request, @AuthenticationPrincipal Jwt jwt) {
        authService.changePassword(Roles.actorOf(jwt), request);
    }

    /** FR-AUTH-05: quên mật khẩu — đặt lại bằng OTP gửi tới SĐT (lấy mã qua {@code POST /api/auth/otp}). Công khai. */
    @PostMapping("/password/reset")
    AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }
}
