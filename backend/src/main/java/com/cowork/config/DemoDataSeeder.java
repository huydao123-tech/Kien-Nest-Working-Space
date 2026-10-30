package com.cowork.config;

import com.cowork.dataaccess.entity.DayType;
import com.cowork.dataaccess.entity.PriceRule;
import com.cowork.dataaccess.entity.Resource;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.repository.PriceRuleRepository;
import com.cowork.dataaccess.repository.ResourceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Dữ liệu mẫu cho môi trường dev: một chi nhánh với đủ loại không gian và bảng giá cao điểm / thấp điểm. */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
class DemoDataSeeder implements CommandLineRunner {

    private static final int PEAK_START = 9 * 60;
    private static final int PEAK_END = 18 * 60;
    private static final int END_OF_DAY = 24 * 60;

    /** Giá mỗi block 30 phút: {cao điểm, thấp điểm}. */
    private static final Map<ResourceType, long[]> PRICES = Map.of(
            ResourceType.HOT_DESK, new long[]{25_000, 18_000},
            ResourceType.FIXED_DESK, new long[]{35_000, 25_000},
            ResourceType.FOCUS_BOOTH, new long[]{45_000, 35_000},
            ResourceType.MEETING_ROOM, new long[]{150_000, 120_000},
            ResourceType.EVENT_SPACE, new long[]{500_000, 400_000});

    private final ResourceRepository resources;
    private final PriceRuleRepository priceRules;

    DemoDataSeeder(ResourceRepository resources, PriceRuleRepository priceRules) {
        this.resources = resources;
        this.priceRules = priceRules;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (resources.count() > 0) {
            return;
        }
        for (int i = 1; i <= 6; i++) {
            resources.save(new Resource("HD-0" + i, "Hot Desk " + i, ResourceType.HOT_DESK, 1));
        }
        for (int i = 1; i <= 3; i++) {
            resources.save(new Resource("FD-0" + i, "Bàn cố định " + i, ResourceType.FIXED_DESK, 1));
        }
        resources.save(new Resource("FB-01", "Focus Booth 1", ResourceType.FOCUS_BOOTH, 1));
        resources.save(new Resource("FB-02", "Focus Booth 2", ResourceType.FOCUS_BOOTH, 1));
        resources.save(new Resource("MR-01", "Phòng họp nhỏ", ResourceType.MEETING_ROOM, 6));
        resources.save(new Resource("MR-02", "Phòng họp lớn", ResourceType.MEETING_ROOM, 12));
        resources.save(new Resource("EV-01", "Không gian sự kiện", ResourceType.EVENT_SPACE, 50));

        PRICES.forEach((type, price) -> {
            long peak = price[0];
            long offPeak = price[1];
            priceRules.save(new PriceRule(type, DayType.WEEKDAY, 0, PEAK_START, offPeak, "Thấp điểm sáng"));
            priceRules.save(new PriceRule(type, DayType.WEEKDAY, PEAK_START, PEAK_END, peak, "Cao điểm"));
            priceRules.save(new PriceRule(type, DayType.WEEKDAY, PEAK_END, END_OF_DAY, offPeak, "Thấp điểm tối"));
            priceRules.save(new PriceRule(type, DayType.WEEKEND, 0, END_OF_DAY, offPeak, "Cuối tuần"));
        });
    }
}
