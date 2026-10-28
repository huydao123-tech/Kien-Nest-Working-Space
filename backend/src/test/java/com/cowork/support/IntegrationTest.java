package com.cowork.support;

import com.cowork.space.domain.Resource;
import com.cowork.space.domain.ResourceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.ZoneId;

/**
 * Nền chung cho integration test: dùng chung một Spring context (và một CSDL H2),
 * nên mỗi test phải dùng ngày / vị trí riêng để không đụng nhau.
 */
@SpringBootTest(properties = "booking.jobs.enabled=false")
@AutoConfigureMockMvc
@Import(IntegrationTest.TestClockConfig.class)
public abstract class IntegrationTest {

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected ResourceRepository resources;

    protected Resource resource(String code) {
        return resources.findAll().stream()
                .filter(r -> r.code().equals(code))
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
