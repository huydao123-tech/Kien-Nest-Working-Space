package com.cowork.business.service;

import com.cowork.business.exception.DomainException;
import com.cowork.config.PricingProperties;
import com.cowork.dataaccess.entity.PriceRule;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.enums.DayType;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import com.cowork.dataaccess.repository.PriceRuleRepository;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dataaccess.valueobject.TimeRange;
import com.cowork.dto.PriceQuote;
import com.cowork.support.TestCaseLogRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BR-03, BR-04, BR-06: tính tiền theo block 30 phút. Viết theo {@code .claude/rules/unit-test.md}.
 * Bảng giá FIXED_DESK trong test: ngày thường 00:00–09:00 và 18:00–24:00 = 25.000, 09:00–18:00 = 35.000; cuối tuần 20.000.
 */
public class PricingServiceTest {

    /** Thứ Hai. */
    private static final LocalDateTime MONDAY = LocalDateTime.of(2026, 11, 2, 0, 0);
    /** Thứ Bảy. */
    private static final LocalDateTime SATURDAY = LocalDateTime.of(2026, 11, 7, 0, 0);
    private static final ResourceTypeCode TYPE = ResourceTypeCode.FIXED_DESK;

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private PriceRuleRepository priceRules;
    private PricingService service;

    @Before
    public void setUp() {
        priceRules = mock(PriceRuleRepository.class);
        service = new PricingService(priceRules, new PricingProperties(30, new BigDecimal("1.5")));
        ResourceType fixedDesk = new ResourceType(TYPE, "Bàn cố định", null);
        when(priceRules.findByResourceTypeCode(TYPE)).thenReturn(List.of(
                new PriceRule(fixedDesk, DayType.WEEKDAY, 0, 540, 25_000, "Thấp điểm sáng"),
                new PriceRule(fixedDesk, DayType.WEEKDAY, 540, 1080, 35_000, "Cao điểm"),
                new PriceRule(fixedDesk, DayType.WEEKDAY, 1080, 1440, 25_000, "Thấp điểm tối"),
                new PriceRule(fixedDesk, DayType.WEEKEND, 0, 1440, 20_000, "Cuối tuần")));
    }

    // ---------- quote ----------

    // Viewpoint: VP3, VP4
    @Test
    public void test_quote_OneBlock_001() {
        // Vòng lặp 1 block: 08:00–08:30 thấp điểm.
        TimeRange range = new TimeRange(MONDAY.withHour(8), MONDAY.withHour(8).withMinute(30));
        log.input("type = {}, range = {}", TYPE, range);

        PriceQuote quote = service.quote(TYPE, range);

        log.output("blocks = {}, blockMinutes = {}, total = {}", quote.blocks(), quote.blockMinutes(), quote.total());
        assertEquals(1, quote.blocks());
        assertEquals(30, quote.blockMinutes());
        assertEquals(Money.vnd(25_000), quote.total());
    }

    // Viewpoint: VP3, VP4
    @Test
    public void test_quote_SpansIntoPeak_002() {
        // Nhiều block vắt qua mốc cao điểm 09:00: 08:30 thấp điểm + 09:00 cao điểm.
        TimeRange range = new TimeRange(MONDAY.withHour(8).withMinute(30), MONDAY.withHour(9).withMinute(30));
        log.input("type = {}, range = {}", TYPE, range);

        PriceQuote quote = service.quote(TYPE, range);

        log.output("blocks = {}, total = {}", quote.blocks(), quote.total());
        assertEquals(2, quote.blocks());
        assertEquals(Money.vnd(60_000), quote.total());
    }

    // Viewpoint: VP3
    @Test
    public void test_quote_OneMinuteOverBlock_003() {
        // Cận trên: 31 phút làm tròn lên 2 block (BR-03).
        TimeRange range = new TimeRange(MONDAY.withHour(8), MONDAY.withHour(8).withMinute(31));
        log.input("type = {}, range = {}", TYPE, range);

        PriceQuote quote = service.quote(TYPE, range);

        log.output("blocks = {}, total = {}", quote.blocks(), quote.total());
        assertEquals(2, quote.blocks());
        assertEquals(Money.vnd(50_000), quote.total());
    }

    // Viewpoint: VP3
    @Test
    public void test_quote_OneSecond_004() {
        // Cận dưới: 1 giây vẫn tính 1 block.
        TimeRange range = new TimeRange(MONDAY.withHour(8), MONDAY.withHour(8).withSecond(1));
        log.input("type = {}, range = {}", TYPE, range);

        PriceQuote quote = service.quote(TYPE, range);

        log.output("blocks = {}", quote.blocks());
        assertEquals(1, quote.blocks());
    }

    // Viewpoint: VP3
    @Test
    public void test_quote_PeakEndIsExclusive_005() {
        // Block bắt đầu đúng 18:00 lấy giá thấp điểm tối (khung giá nửa mở).
        TimeRange range = new TimeRange(MONDAY.withHour(17).withMinute(30), MONDAY.withHour(18).withMinute(30));
        log.input("type = {}, range = {}", TYPE, range);

        PriceQuote quote = service.quote(TYPE, range);

        log.output("total = {}", quote.total());
        assertEquals(Money.vnd(35_000 + 25_000), quote.total());
    }

    // Viewpoint: VP3
    @Test
    public void test_quote_Weekend_006() {
        // Thứ Bảy dùng giá cuối tuần.
        TimeRange range = new TimeRange(SATURDAY.withHour(10), SATURDAY.withHour(11));
        log.input("type = {}, range = {}", TYPE, range);

        PriceQuote quote = service.quote(TYPE, range);

        log.output("total = {}", quote.total());
        assertEquals(Money.vnd(40_000), quote.total());
    }

    // Viewpoint: VP3, VP4
    @Test
    public void test_quote_NoRules_007() {
        // Loại chỗ chưa có mức giá nào (0 record).
        when(priceRules.findByResourceTypeCode(ResourceTypeCode.HOT_DESK)).thenReturn(List.of());
        TimeRange range = new TimeRange(MONDAY.withHour(8), MONDAY.withHour(9));
        log.input("type = {}, range = {}", ResourceTypeCode.HOT_DESK, range);

        DomainException thrown = assertThrows(DomainException.class, () -> service.quote(ResourceTypeCode.HOT_DESK, range));

        log.output("code = {}", thrown.code());
        assertEquals("NO_PRICE_RULE", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_quote_RuleOfOtherTypeIgnored_008() {
        // Danh sách có mức giá của loại khác: không dùng nhầm.
        ResourceType booth = new ResourceType(ResourceTypeCode.FOCUS_BOOTH, "Booth", null);
        when(priceRules.findByResourceTypeCode(ResourceTypeCode.HOT_DESK))
                .thenReturn(List.of(new PriceRule(booth, DayType.WEEKDAY, 0, 1440, 45_000, "Cả ngày")));
        TimeRange range = new TimeRange(MONDAY.withHour(8), MONDAY.withHour(9));
        log.input("type = {}, range = {}", ResourceTypeCode.HOT_DESK, range);

        DomainException thrown = assertThrows(DomainException.class, () -> service.quote(ResourceTypeCode.HOT_DESK, range));

        log.output("code = {}", thrown.code());
        assertEquals("NO_PRICE_RULE", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_quote_NullRange_009() {
        // Khoảng thời gian null.
        TimeRange range = null;
        log.input("type = {}, range = {}", TYPE, range);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.quote(TYPE, range));

        log.output("exception = {}", thrown.getClass().getSimpleName());
        assertEquals(NullPointerException.class, thrown.getClass());
    }

    // ---------- overstaySurcharge ----------

    // Viewpoint: VP3
    @Test
    public void test_overstaySurcharge_Multiplier_010() {
        // Phụ phí = giá block vượt × hệ số 1,5: quá 10 phút lúc 18:00 → 1 block 25.000 × 1,5.
        TimeRange overstay = new TimeRange(MONDAY.withHour(18), MONDAY.withHour(18).withMinute(10));
        log.input("type = {}, overstay = {}", TYPE, overstay);

        Money surcharge = service.overstaySurcharge(TYPE, overstay);

        log.output("surcharge = {}", surcharge);
        assertEquals(Money.vnd(37_500), surcharge);
    }

    // Viewpoint: VP3
    @Test
    public void test_overstaySurcharge_RoundsHalfUp_011() {
        // Làm tròn nửa lên: 25.000 × 1,00002 = 25.000,5 → 25.001.
        service = new PricingService(priceRules, new PricingProperties(30, new BigDecimal("1.00002")));
        TimeRange overstay = new TimeRange(MONDAY.withHour(18), MONDAY.withHour(18).withMinute(30));
        log.input("type = {}, overstay = {}", TYPE, overstay);

        Money surcharge = service.overstaySurcharge(TYPE, overstay);

        log.output("surcharge = {}", surcharge);
        assertEquals(Money.vnd(25_001), surcharge);
    }
}
