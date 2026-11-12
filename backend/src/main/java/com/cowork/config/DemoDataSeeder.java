package com.cowork.config;

import com.cowork.dataaccess.entity.Addon;
import com.cowork.dataaccess.entity.Amenity;
import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.entity.MembershipPlan;
import com.cowork.dataaccess.entity.MembershipPlanResourceType;
import com.cowork.dataaccess.entity.ResourceAmenity;
import com.cowork.dataaccess.entity.Staff;
import com.cowork.dataaccess.entity.Voucher;
import com.cowork.dataaccess.entity.VoucherResourceType;
import com.cowork.dataaccess.entity.Zone;
import com.cowork.dataaccess.enums.DayType;
import com.cowork.dataaccess.entity.PriceRule;
import com.cowork.dataaccess.entity.SpaceResource;
import com.cowork.dataaccess.enums.DiscountType;
import com.cowork.dataaccess.enums.MembershipPlanStatus;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import com.cowork.dataaccess.entity.ResourceType;
import com.cowork.dataaccess.enums.StaffRole;
import com.cowork.dataaccess.enums.StaffStatus;
import com.cowork.dataaccess.enums.VoucherStatus;
import com.cowork.dataaccess.repository.AddonRepository;
import com.cowork.dataaccess.repository.AmenityRepository;
import com.cowork.dataaccess.repository.BranchRepository;
import com.cowork.dataaccess.repository.MembershipPlanRepository;
import com.cowork.dataaccess.repository.PriceRuleRepository;
import com.cowork.dataaccess.repository.SpaceResourceRepository;
import com.cowork.dataaccess.repository.ResourceTypeRepository;
import com.cowork.dataaccess.repository.StaffRepository;
import com.cowork.dataaccess.repository.VoucherRepository;
import com.cowork.dataaccess.repository.ZoneRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Dữ liệu mẫu cho môi trường dev: một chi nhánh (07:00–22:00) với 3 khu vực, đủ loại không gian có tọa độ trên sơ đồ,
 * tiện ích và bảng giá cao điểm / thấp điểm; tài khoản nhân viên đủ 4 vai trò (mật khẩu {@value #DEMO_PASSWORD}),
 * add-on, voucher và gói thành viên. Mã chỗ, tiện ích, email nhân viên, mã add-on / voucher giống db/data.sql.
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
class DemoDataSeeder implements CommandLineRunner {

    private static final int PEAK_START = 9 * 60;
    private static final int PEAK_END = 18 * 60;
    private static final int END_OF_DAY = 24 * 60;

    /** Giá mỗi block 30 phút: {cao điểm, thấp điểm}. */
    private static final Map<ResourceTypeCode, long[]> PRICES = Map.of(
            ResourceTypeCode.HOT_DESK, new long[]{25_000, 18_000},
            ResourceTypeCode.FIXED_DESK, new long[]{35_000, 25_000},
            ResourceTypeCode.FOCUS_BOOTH, new long[]{45_000, 35_000},
            ResourceTypeCode.MEETING_ROOM, new long[]{150_000, 120_000},
            ResourceTypeCode.EVENT_SPACE, new long[]{500_000, 400_000});

    /** Tên hiển thị của từng loại, trùng với dữ liệu mẫu trong db/schema.sql. */
    private static final Map<ResourceTypeCode, String> TYPE_NAMES = Map.of(
            ResourceTypeCode.HOT_DESK, "Chỗ ngồi linh hoạt",
            ResourceTypeCode.FIXED_DESK, "Bàn cố định",
            ResourceTypeCode.FOCUS_BOOTH, "Pod cá nhân",
            ResourceTypeCode.MEETING_ROOM, "Phòng họp",
            ResourceTypeCode.EVENT_SPACE, "Không gian sự kiện");

    /** Mật khẩu chung của các tài khoản nhân viên mẫu (chỉ dùng cho dev / test). */
    static final String DEMO_PASSWORD = "Cowork@123";

    private final ResourceTypeRepository resourceTypes;
    private final SpaceResourceRepository resources;
    private final PriceRuleRepository priceRules;
    private final BranchRepository branches;
    private final ZoneRepository zones;
    private final AmenityRepository amenities;
    private final StaffRepository staff;
    private final AddonRepository addons;
    private final VoucherRepository vouchers;
    private final MembershipPlanRepository plans;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    DemoDataSeeder(ResourceTypeRepository resourceTypes, SpaceResourceRepository resources, PriceRuleRepository priceRules,
                   BranchRepository branches, ZoneRepository zones, AmenityRepository amenities, StaffRepository staff,
                   AddonRepository addons, VoucherRepository vouchers, MembershipPlanRepository plans,
                   PasswordEncoder passwordEncoder, Clock clock) {
        this.resourceTypes = resourceTypes;
        this.resources = resources;
        this.priceRules = priceRules;
        this.branches = branches;
        this.zones = zones;
        this.amenities = amenities;
        this.staff = staff;
        this.addons = addons;
        this.vouchers = vouchers;
        this.plans = plans;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (resources.count() > 0) {
            return;
        }
        // PostgreSQL đã có sẵn các loại từ db/schema.sql; H2 tạo bảng rỗng nên phải thêm ở đây.
        Map<ResourceTypeCode, ResourceType> types = new EnumMap<>(ResourceTypeCode.class);
        for (ResourceTypeCode code : ResourceTypeCode.values()) {
            types.put(code, resourceTypes.findByCode(code)
                    .orElseGet(() -> resourceTypes.save(new ResourceType(code, TYPE_NAMES.get(code), null))));
        }
        Branch branch = branches.save(new Branch("HCM-Q1", "Cowork Nguyễn Huệ",
                "72 Nguyễn Huệ, phường Bến Nghé, Quận 1, TP. Hồ Chí Minh", LocalTime.of(7, 0), LocalTime.of(22, 0)));
        Zone open = zones.save(new Zone(branch, "Khu làm việc mở", 1, "/floor-plans/hcm-q1-tang-1.png"));
        Zone quiet = zones.save(new Zone(branch, "Khu yên tĩnh", 2, "/floor-plans/hcm-q1-tang-2.png"));
        Zone meeting = zones.save(new Zone(branch, "Khu phòng họp và sự kiện", 3, "/floor-plans/hcm-q1-tang-3.png"));

        Amenity outlet = amenities.save(new Amenity("POWER_OUTLET", "Ổ cắm điện"));
        Amenity monitor = amenities.save(new Amenity("MONITOR", "Màn hình rời"));
        Amenity whiteboard = amenities.save(new Amenity("WHITEBOARD", "Bảng trắng"));
        Amenity tv = amenities.save(new Amenity("TV", "TV trình chiếu"));
        Amenity silent = amenities.save(new Amenity("QUIET", "Khu yên tĩnh"));
        Amenity sound = amenities.save(new Amenity("SOUND_SYSTEM", "Hệ thống âm thanh"));

        // Tọa độ là % kích thước ảnh sơ đồ của khu vực.
        ResourceType hotDesk = types.get(ResourceTypeCode.HOT_DESK);
        for (int i = 1; i <= 6; i++) {
            int x = 15 * ((i - 1) % 3 + 1);
            int y = i <= 3 ? 30 : 60;
            save("HD-0" + i, "Hot Desk " + i, hotDesk, 1, open, x, y, Map.of(outlet, 2));
        }
        ResourceType fixedDesk = types.get(ResourceTypeCode.FIXED_DESK);
        for (int i = 1; i <= 3; i++) {
            save("FD-0" + i, "Bàn cố định " + i, fixedDesk, 1, quiet, 20 * i, 25, Map.of(outlet, 4, monitor, 1));
        }
        ResourceType booth = types.get(ResourceTypeCode.FOCUS_BOOTH);
        save("FB-01", "Focus Booth 1", booth, 1, quiet, 25, 70, Map.of(outlet, 2, silent, 1));
        save("FB-02", "Focus Booth 2", booth, 1, quiet, 50, 70, Map.of(outlet, 2, silent, 1));
        ResourceType room = types.get(ResourceTypeCode.MEETING_ROOM);
        save("MR-01", "Phòng họp nhỏ", room, 6, meeting, 25, 30, Map.of(outlet, 6, whiteboard, 1, tv, 1));
        save("MR-02", "Phòng họp lớn", room, 12, meeting, 70, 30, Map.of(outlet, 12, whiteboard, 2, tv, 1));
        save("EV-01", "Không gian sự kiện", types.get(ResourceTypeCode.EVENT_SPACE), 50, meeting, 50, 75,
                Map.of(outlet, 20, sound, 1, tv, 2));

        PRICES.forEach((type, price) -> {
            long peak = price[0];
            long offPeak = price[1];
            ResourceType entity = types.get(type);
            priceRules.save(new PriceRule(entity, DayType.WEEKDAY, 0, PEAK_START, offPeak, "Thấp điểm sáng"));
            priceRules.save(new PriceRule(entity, DayType.WEEKDAY, PEAK_START, PEAK_END, peak, "Cao điểm"));
            priceRules.save(new PriceRule(entity, DayType.WEEKDAY, PEAK_END, END_OF_DAY, offPeak, "Thấp điểm tối"));
            priceRules.save(new PriceRule(entity, DayType.WEEKEND, 0, END_OF_DAY, offPeak, "Cuối tuần"));
        });

        seedStaff(branch);
        // FR-BOOK-02: add-on của chi nhánh, đơn giá một block 30 phút
        addons.save(new Addon(branch, "PROJECTOR", "Máy chiếu", 2, 30_000));
        addons.save(new Addon(branch, "EXTRA_MONITOR", "Màn hình phụ 27 inch", 5, 10_000));
        addons.save(new Addon(branch, "MIC", "Micro không dây", 4, 15_000));
        seedVouchers(types);
        seedPlans(types);
    }

    /** UC23: mỗi vai trò một tài khoản, cùng mật khẩu {@value #DEMO_PASSWORD}; một tài khoản bị khóa. */
    private void seedStaff(Branch branch) {
        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        LocalDateTime now = LocalDateTime.now(clock);
        List<Staff> members = List.of(
                new Staff(null, "Quản trị hệ thống", "admin@cowork.example", hash, StaffRole.ADMIN, 0),
                new Staff(branch, "Đỗ Thu Hà", "ha.do@cowork.example", hash, StaffRole.MANAGER, 1_000_000),
                new Staff(branch, "Ngô Gia Huy", "huy.ngo@cowork.example", hash, StaffRole.RECEPTIONIST, 100_000),
                new Staff(branch, "Bùi Văn Khoa", "khoa.bui@cowork.example", hash, StaffRole.TECHNICIAN, 0),
                new Staff(branch, "Hoàng Mai Lan", "lan.hoang@cowork.example", hash, StaffRole.RECEPTIONIST, 100_000));
        members.forEach(m -> m.setCreatedAt(now));
        members.get(4).setStatus(StaffStatus.LOCKED);
        staff.saveAll(members);
    }

    /** BR-12: voucher mẫu giống db/data.sql. */
    private void seedVouchers(Map<ResourceTypeCode, ResourceType> types) {
        Voucher welcome = new Voucher("WELCOME10", "Giảm 10% cho khách mới", DiscountType.PERCENT, 10,
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 12, 31, 23, 59));
        welcome.setMaxDiscountAmount(50_000L);
        Voucher meeting = new Voucher("HOP50K", "Giảm 50.000đ khi đặt phòng họp", DiscountType.AMOUNT, 50_000,
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 12, 31, 23, 59));
        meeting.setMinOrderAmount(300_000);
        meeting.setTotalLimit(100);
        meeting.setPerCustomerLimit(2);
        meeting.getResourceTypes().add(new VoucherResourceType(meeting, types.get(ResourceTypeCode.MEETING_ROOM)));
        meeting.getResourceTypes().add(new VoucherResourceType(meeting, types.get(ResourceTypeCode.EVENT_SPACE)));
        Voucher sorry = new Voucher("XINLOI30", "Tặng 30 phút đền bù sự cố", DiscountType.FREE_MINUTES, 30,
                LocalDateTime.of(2026, 10, 6, 0, 0), LocalDateTime.of(2027, 1, 6, 23, 59));
        sorry.setTotalLimit(1);
        Voucher tet = new Voucher("TET2026", "Khuyến mãi Tết (tạm dừng)", DiscountType.PERCENT, 20,
                LocalDateTime.of(2026, 1, 20, 0, 0), LocalDateTime.of(2026, 2, 20, 23, 59));
        tet.setMinOrderAmount(100_000);
        tet.setMaxDiscountAmount(100_000L);
        tet.setTotalLimit(500);
        tet.setStatus(VoucherStatus.PAUSED);
        vouchers.saveAll(List.of(welcome, meeting, sorry, tet));
    }

    /** BR-13: gói thành viên mẫu giống db/data.sql. */
    private void seedPlans(Map<ResourceTypeCode, ResourceType> types) {
        MembershipPlan hotDesk = new MembershipPlan("Gói Hot Desk 20 giờ", 1200, 30, 900_000);
        hotDesk.getResourceTypes().add(new MembershipPlanResourceType(hotDesk, types.get(ResourceTypeCode.HOT_DESK)));
        MembershipPlan flexible = new MembershipPlan("Gói Linh hoạt 50 giờ", 3000, 60, 2_000_000);
        for (ResourceTypeCode code : List.of(ResourceTypeCode.HOT_DESK, ResourceTypeCode.FIXED_DESK, ResourceTypeCode.FOCUS_BOOTH)) {
            flexible.getResourceTypes().add(new MembershipPlanResourceType(flexible, types.get(code)));
        }
        MembershipPlan trial = new MembershipPlan("Gói thử 5 giờ", 300, 7, 200_000);
        trial.setStatus(MembershipPlanStatus.STOPPED);
        trial.getResourceTypes().add(new MembershipPlanResourceType(trial, types.get(ResourceTypeCode.HOT_DESK)));
        plans.saveAll(List.of(hotDesk, flexible, trial));
    }

    private void save(String code, String name, ResourceType type, int capacity, Zone zone, int posX, int posY,
                      Map<Amenity, Integer> equipment) {
        SpaceResource resource = new SpaceResource(code, name, type, capacity);
        resource.setZone(zone);
        resource.setPosX(posX);
        resource.setPosY(posY);
        equipment.forEach((amenity, quantity) -> {
            ResourceAmenity ra = new ResourceAmenity(amenity, quantity);
            ra.setResource(resource);
            resource.getAmenities().add(ra);
        });
        resources.save(resource);
    }
}
