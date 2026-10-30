package com.cowork.business.service;

import com.cowork.business.exception.DomainException;
import com.cowork.dataaccess.entity.DayType;
import com.cowork.dataaccess.entity.Money;
import com.cowork.dataaccess.entity.PriceRule;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.entity.TimeRange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingPolicyTest {

    private static final LocalDateTime MONDAY = LocalDateTime.of(2026, 11, 2, 0, 0);
    private static final LocalDateTime SATURDAY = LocalDateTime.of(2026, 11, 7, 0, 0);

    private final PricingPolicy policy = new PricingPolicy(30);
    private final List<PriceRule> rules = List.of(
            new PriceRule(ResourceType.HOT_DESK, DayType.WEEKDAY, 0, 9 * 60, 18_000, "off"),
            new PriceRule(ResourceType.HOT_DESK, DayType.WEEKDAY, 9 * 60, 18 * 60, 25_000, "peak"),
            new PriceRule(ResourceType.HOT_DESK, DayType.WEEKDAY, 18 * 60, 24 * 60, 18_000, "off"),
            new PriceRule(ResourceType.HOT_DESK, DayType.WEEKEND, 0, 24 * 60, 18_000, "weekend"));

    @Test
    void pricesEveryBlockAtPeakRate() {
        PriceQuote quote = policy.quote(ResourceType.HOT_DESK, range(MONDAY.withHour(9), MONDAY.withHour(11)), rules);

        assertThat(quote.blocks()).isEqualTo(4);
        assertThat(quote.total()).isEqualTo(Money.vnd(100_000));
    }

    @Test
    void splitsBookingThatCrossesFromOffPeakIntoPeak() {
        PriceQuote quote = policy.quote(ResourceType.HOT_DESK, range(MONDAY.withHour(8), MONDAY.withHour(10)), rules);

        assertThat(quote.total()).isEqualTo(Money.vnd(2 * 18_000 + 2 * 25_000));
    }

    @Test
    void roundsPartialBlockUp() {
        PriceQuote quote = policy.quote(ResourceType.HOT_DESK,
                range(MONDAY.withHour(9), MONDAY.withHour(9).withMinute(40)), rules);

        assertThat(quote.blocks()).isEqualTo(2);
        assertThat(quote.total()).isEqualTo(Money.vnd(50_000));
    }

    @Test
    void usesWeekendRate() {
        PriceQuote quote = policy.quote(ResourceType.HOT_DESK, range(SATURDAY.withHour(10), SATURDAY.withHour(11)), rules);

        assertThat(quote.total()).isEqualTo(Money.vnd(36_000));
    }

    @Test
    void appliesOverstayMultiplier() {
        Money surcharge = policy.surcharge(ResourceType.HOT_DESK,
                range(MONDAY.withHour(11), MONDAY.withHour(11).withMinute(20)), new BigDecimal("1.5"), rules);

        assertThat(surcharge).isEqualTo(Money.vnd(37_500));
    }

    @Test
    void rejectsTimeWithoutPriceRule() {
        assertThatThrownBy(() -> policy.quote(ResourceType.MEETING_ROOM, range(MONDAY.withHour(9), MONDAY.withHour(10)), rules))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", "NO_PRICE_RULE");
    }

    private static TimeRange range(LocalDateTime start, LocalDateTime end) {
        return new TimeRange(start, end);
    }
}
