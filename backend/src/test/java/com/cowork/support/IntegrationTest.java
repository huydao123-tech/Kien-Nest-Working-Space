package com.cowork.support;

import com.cowork.business.service.AuthService;
import com.cowork.business.service.OtpGenerator;
import com.cowork.dataaccess.entity.SpaceResource;
import com.cowork.dataaccess.repository.SpaceResourceRepository;
import com.cowork.dto.AuthDtos.StaffLoginRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.ZoneId;

/**
 * Nền chung cho integration test: chạy trên H2 (không cần PostgreSQL), dùng chung một Spring context và một CSDL,
 * nên mỗi test phải dùng ngày / vị trí riêng để không đụng nhau.
 *
 * <p>Tài khoản nhân viên mẫu do {@code DemoDataSeeder} tạo, mật khẩu {@value #DEMO_PASSWORD}.
 * Mã OTP trong test luôn là {@value #TEST_OTP}.
 */
@SpringBootTest(properties = "booking.jobs.enabled=false")
@ActiveProfiles("h2")
@AutoConfigureMockMvc
@Import(IntegrationTest.TestConfig.class)
public abstract class IntegrationTest {

    protected static final String DEMO_PASSWORD = "Cowork@123";
    protected static final String TEST_OTP = "123456";
    protected static final String AUTHORIZATION = "Authorization";

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected SpaceResourceRepository resources;

    @Autowired
    protected AuthService authService;

    protected SpaceResource resource(String code) {
        return resources.findAll().stream()
                .filter(r -> r.getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    /** Header {@code Authorization} của nhân viên mẫu; token phát theo đồng hồ test hiện tại nên luôn còn hạn. */
    protected String staffAuth(String email) {
        return "Bearer " + authService.staffLogin(new StaffLoginRequest(email, DEMO_PASSWORD)).accessToken();
    }

    protected String adminAuth() {
        return staffAuth("admin@cowork.example");
    }

    protected String managerAuth() {
        return staffAuth("ha.do@cowork.example");
    }

    protected String receptionistAuth() {
        return staffAuth("huy.ngo@cowork.example");
    }

    protected String technicianAuth() {
        return staffAuth("khoa.bui@cowork.example");
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(ZoneId.of("Asia/Ho_Chi_Minh"));
        }

        /** SMS chỉ ghi log, nên test dùng mã OTP cố định thay vì đọc tin nhắn. */
        @Bean
        @Primary
        OtpGenerator fixedOtpGenerator() {
            return new OtpGenerator() {
                @Override
                public String next() {
                    return TEST_OTP;
                }
            };
        }
    }
}
