package com.cowork.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "pricing")
public record PricingProperties(
        @DefaultValue("30") int blockMinutes,
        @DefaultValue("1.0") BigDecimal overstayMultiplier) {
}
