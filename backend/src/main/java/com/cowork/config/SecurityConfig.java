package com.cowork.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * UC01, UC23: xác thực bằng JWT (HS256, không lưu phiên) và phân quyền theo vai trò (RBAC).
 * <ul>
 *   <li>Tại đây chỉ chia API thành "công khai" và "phải đăng nhập".</li>
 *   <li>Vai trò nào được gọi API nào ghi bằng {@code @PreAuthorize} ngay trên từng hàm controller.</li>
 *   <li>Role lấy từ claim {@code roles} của token: {@code ADMIN}, {@code MANAGER}, {@code RECEPTIONIST},
 *       {@code TECHNICIAN} (nhân viên) hoặc {@code CUSTOMER} (khách có tài khoản).</li>
 * </ul>
 * Lỗi 401 / 403 trả về {@link ProblemDetail} có trường {@code code} giống mọi lỗi khác của hệ thống.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig {

    /** API ai cũng gọi được, kể cả không gửi token: đăng ký / đăng nhập, tra cứu chỗ trống, khách vãng lai đặt chỗ và thanh toán. */
    private static final String[] PUBLIC_GET = {
            "/api/branches", "/api/branches/*/floor-plan", "/api/amenities", "/api/availability", "/api/addons"};
    private static final String[] PUBLIC_POST = {
            "/api/auth/otp", "/api/auth/register/phone", "/api/auth/register/email",
            "/api/auth/login", "/api/auth/login/otp", "/api/auth/staff/login", "/api/auth/password/reset",
            "/api/bookings", "/api/bookings/*/payments",
            "/api/payments/webhook", "/api/payments/sandbox/**"};

    @Bean
    SecurityFilterChain api(HttpSecurity http, JwtDecoder jwtDecoder, ObjectMapper objectMapper) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // /h2-console (chỉ có ở profile h2) hiển thị trong frame
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        .requestMatchers("/h2-console/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.decoder(jwtDecoder).jwtAuthenticationConverter(rolesFromClaim()))
                        .authenticationEntryPoint((req, res, e) -> writeProblem(res, objectMapper, HttpStatus.UNAUTHORIZED,
                                "UNAUTHORIZED", "Chưa đăng nhập hoặc token không hợp lệ / đã hết hạn")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeProblem(res, objectMapper, HttpStatus.UNAUTHORIZED,
                                "UNAUTHORIZED", "Chưa đăng nhập hoặc token không hợp lệ / đã hết hạn"))
                        .accessDeniedHandler((req, res, ex) -> writeProblem(res, objectMapper, HttpStatus.FORBIDDEN,
                                "FORBIDDEN", "Tài khoản không có quyền thực hiện thao tác này")));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(AuthProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    /** Hạn token so với {@link Clock} của ứng dụng (test tua được đồng hồ), và chỉ nhận token do chính hệ thống phát hành. */
    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties)).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(properties.jwtIssuer())));
        return decoder;
    }

    private static SecretKey secretKey(AuthProperties properties) {
        byte[] bytes = properties.jwtSecret() == null ? new byte[0] : properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("auth.jwt-secret phải dài tối thiểu 32 byte (HS256)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /** Claim {@code roles: ["MANAGER"]} → quyền {@code ROLE_MANAGER}, dùng được trong {@code hasRole('MANAGER')}. */
    private static JwtAuthenticationConverter rolesFromClaim() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static void writeProblem(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status,
                                     String code, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
