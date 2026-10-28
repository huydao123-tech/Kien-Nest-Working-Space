package com.cowork.shared.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
class TimeConfig {

    /** Mọi mốc thời gian nghiệp vụ lấy từ Clock này để thống nhất múi giờ và kiểm thử được. */
    @Bean
    Clock clock(@Value("${app.time-zone}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
