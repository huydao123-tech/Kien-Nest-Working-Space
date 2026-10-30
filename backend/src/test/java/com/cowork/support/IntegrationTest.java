package com.cowork.support;

import com.cowork.dataaccess.entity.Resource;
import com.cowork.dataaccess.repository.ResourceRepository;
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
 */
@SpringBootTest(properties = "booking.jobs.enabled=false")
@ActiveProfiles("h2")
@AutoConfigureMockMvc
@Import(IntegrationTest.TestClockConfig.class)
public abstract class IntegrationTest {

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected ResourceRepository resources;

    protected Resource resource(String code) {
        return resources.findAll().stream()
                .filter(r -> r.getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    @TestConfiguration
    static class TestClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }
}
