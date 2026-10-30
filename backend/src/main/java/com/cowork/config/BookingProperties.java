package com.cowork.config;

import com.cowork.business.service.BookingRules;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "booking")
public record BookingProperties(
        @DefaultValue("10m") Duration holdDuration,
        @DefaultValue("15m") Duration checkInEarlyWindow,
        @DefaultValue("5m") Duration overstayGrace) {

    public BookingRules toRules() {
        return new BookingRules(holdDuration, checkInEarlyWindow, overstayGrace);
    }
}
