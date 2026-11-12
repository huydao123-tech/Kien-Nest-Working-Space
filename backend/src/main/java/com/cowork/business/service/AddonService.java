package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.Addon;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.BookingAddon;
import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.enums.AddonStatus;
import com.cowork.dataaccess.enums.BookingStatus;
import com.cowork.dataaccess.repository.AddonRepository;
import com.cowork.dataaccess.repository.BookingAddonRepository;
import com.cowork.dataaccess.repository.BookingAddonRepository.Reservation;
import com.cowork.dataaccess.repository.BranchRepository;
import com.cowork.dataaccess.valueobject.Money;
import com.cowork.dataaccess.valueobject.TimeRange;
import com.cowork.dto.BookingDtos.AddonSelection;
import com.cowork.dto.CommerceDtos.AddonDetail;
import com.cowork.dto.CommerceDtos.AddonRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * FR-BOOK-02, FR-ADM-07: add-on thuê kèm (máy chiếu, màn hình phụ...).
 * <ul>
 *   <li>Add-on thuộc một chi nhánh, chỉ thuê kèm được vị trí cùng chi nhánh.</li>
 *   <li>Tồn kho tính theo khung giờ: tại mọi thời điểm trong khung giờ đặt, tổng số lượng các đơn đang chiếm chỗ
 *       (HELD, CONFIRMED, CHECKED_IN, OVERSTAY) cộng số lượng mới không vượt {@code addon.stock}.</li>
 *   <li>Tiền add-on = đơn giá block × số block của khung giờ × số lượng.</li>
 * </ul>
 * Dòng add-on được khóa ghi (FOR UPDATE) trước khi đếm, theo thứ tự id tăng dần để hai đơn không khóa chéo nhau.
 */
@Service
@Transactional
public class AddonService {

    private final AddonRepository addons;
    private final BookingAddonRepository bookingAddons;
    private final BranchRepository branches;

    public AddonService(AddonRepository addons, BookingAddonRepository bookingAddons, BranchRepository branches) {
        this.addons = addons;
        this.bookingAddons = bookingAddons;
        this.branches = branches;
    }

    /** Một dòng add-on đã kiểm tra tồn kho, sẵn sàng gắn vào đơn. */
    public record AddonLine(Addon addon, int quantity, long unitPrice, Money amount) {
    }

    /**
     * FR-BOOK-02: kiểm tra và tính tiền các add-on khách chọn cho khung giờ {@code period} ({@code blocks} block).
     * Danh sách rỗng → không thuê add-on.
     */
    public List<AddonLine> reserve(Branch branch, TimeRange period, int blocks, List<AddonSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            return List.of();
        }
        Set<Long> seen = new HashSet<>();
        for (AddonSelection s : selections) {
            if (!seen.add(s.addonId())) {
                throw new DomainException("DUPLICATE_ADDON", "Add-on " + s.addonId() + " bị chọn lặp, hãy gộp số lượng");
            }
        }
        List<AddonLine> lines = new ArrayList<>();
        for (AddonSelection s : selections.stream().sorted(Comparator.comparing(AddonSelection::addonId)).toList()) {
            Addon addon = lockUsable(s.addonId(), branch);
            requireStock(addon, s.quantity(), period, null);
            long unitPrice = addon.getPricePerBlock();
            lines.add(new AddonLine(addon, s.quantity(), unitPrice, Money.vnd(unitPrice * blocks * s.quantity())));
        }
        return lines;
    }

    /**
     * Đổi chỗ / khung giờ: các add-on đã thuê của đơn phải còn đủ trong khung giờ mới (không tính chính đơn đó)
     * và thuộc chi nhánh của vị trí mới ({@code newBranch}).
     */
    public void recheck(Booking booking, Branch newBranch, TimeRange newPeriod) {
        boolean otherBranch = booking.getAddons().stream()
                .anyMatch(ba -> newBranch == null || !ba.getAddon().getBranch().getId().equals(newBranch.getId()));
        if (otherBranch) {
            throw new ConflictException("ADDON_NOT_AVAILABLE",
                    "Đơn có add-on của chi nhánh cũ, không đổi được sang vị trí ở chi nhánh khác");
        }
        List<BookingAddon> rented = booking.getAddons().stream()
                .sorted(Comparator.comparing(ba -> ba.getAddon().getId()))
                .toList();
        for (BookingAddon ba : rented) {
            Addon addon = addons.findByIdForUpdate(ba.getAddon().getId())
                    .orElseThrow(() -> new NotFoundException("Add-on", ba.getAddon().getId()));
            requireStock(addon, ba.getQuantity(), newPeriod, booking.getId());
        }
    }

    private Addon lockUsable(Long addonId, Branch branch) {
        Addon addon = addons.findByIdForUpdate(addonId).orElseThrow(() -> new NotFoundException("Add-on", addonId));
        boolean sameBranch = branch != null && addon.getBranch().getId().equals(branch.getId());
        if (addon.getStatus() != AddonStatus.ACTIVE || !sameBranch) {
            throw new ConflictException("ADDON_NOT_AVAILABLE", "Add-on " + addon.getCode() + " không cho thuê tại vị trí này");
        }
        return addon;
    }

    private void requireStock(Addon addon, int quantity, TimeRange period, Long excludeBookingId) {
        Long exclude = excludeBookingId == null ? 0L : excludeBookingId;
        List<Reservation> reservations = new ArrayList<>(bookingAddons.findReservations(addon.getId(), period.startAt(),
                period.endAt(), BookingStatus.SLOT_OCCUPYING, exclude));
        reservations.addAll(bookingAddons.findPendingReservations(addon.getId(), period.startAt(), period.endAt(), exclude));
        int available = addon.getStock() - peakUsage(reservations, period);
        if (quantity > available) {
            throw new ConflictException("ADDON_OUT_OF_STOCK", "Add-on " + addon.getCode() + " chỉ còn "
                    + Math.max(available, 0) + " trong khung giờ này");
        }
    }

    /**
     * Số lượng đang dùng nhiều nhất tại một thời điểm trong {@code window}. Khoảng nửa mở: đơn kết thúc 11:00 và đơn
     * bắt đầu 11:00 không cộng dồn (cùng mốc thì cộng trừ gộp lại trước khi so).
     */
    static int peakUsage(List<Reservation> reservations, TimeRange window) {
        Map<LocalDateTime, Integer> delta = new TreeMap<>();
        for (Reservation r : reservations) {
            LocalDateTime start = r.getStartAt().isBefore(window.startAt()) ? window.startAt() : r.getStartAt();
            LocalDateTime end = r.getEndAt().isAfter(window.endAt()) ? window.endAt() : r.getEndAt();
            if (end.isAfter(start)) {
                delta.merge(start, r.getQuantity(), Integer::sum);
                delta.merge(end, -r.getQuantity(), Integer::sum);
            }
        }
        int running = 0;
        int peak = 0;
        for (int change : delta.values()) {
            running += change;
            peak = Math.max(peak, running);
        }
        return peak;
    }

    // ------------------------------------------------------------------
    // Danh mục add-on
    // ------------------------------------------------------------------

    /** Add-on đang cho thuê của một chi nhánh — khách xem để chọn khi đặt chỗ. */
    @Transactional(readOnly = true)
    public List<AddonDetail> listActive(Long branchId) {
        return addons.findByBranchIdAndStatusOrderByCodeAsc(branchId, AddonStatus.ACTIVE).stream().map(AddonDetail::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AddonDetail> list() {
        return addons.findAllByOrderByIdAsc().stream().map(AddonDetail::from).toList();
    }

    @Transactional(readOnly = true)
    public AddonDetail get(Long id) {
        return AddonDetail.from(load(id));
    }

    public AddonDetail create(AddonRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (addons.existsByBranchIdAndCodeIgnoreCase(request.branchId(), code)) {
            throw new ConflictException("CODE_ALREADY_EXISTS", "Chi nhánh đã có add-on mã " + code);
        }
        Addon addon = new Addon();
        addon.setStatus(AddonStatus.ACTIVE);
        apply(addon, request, code);
        return AddonDetail.from(addons.save(addon));
    }

    /** Giảm tồn kho không ảnh hưởng đơn đã đặt; chỉ đơn mới bị chặn nếu vượt số còn lại. */
    public AddonDetail update(Long id, AddonRequest request) {
        Addon addon = load(id);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        boolean codeChanged = !code.equals(addon.getCode()) || !request.branchId().equals(addon.getBranch().getId());
        if (codeChanged && addons.existsByBranchIdAndCodeIgnoreCase(request.branchId(), code)) {
            throw new ConflictException("CODE_ALREADY_EXISTS", "Chi nhánh đã có add-on mã " + code);
        }
        apply(addon, request, code);
        return AddonDetail.from(addons.save(addon));
    }

    /** Add-on đã từng được thuê thì không xóa được (409 DATA_CONFLICT) — chuyển INACTIVE để ngưng cho thuê. */
    public void delete(Long id) {
        addons.delete(load(id));
        addons.flush();
    }

    private void apply(Addon addon, AddonRequest request, String code) {
        addon.setBranch(branches.findById(request.branchId())
                .orElseThrow(() -> new NotFoundException("Chi nhánh", request.branchId())));
        addon.setCode(code);
        addon.setName(request.name().trim());
        addon.setStock(request.stock());
        addon.setPricePerBlock(request.pricePerBlock());
        if (request.status() != null) {
            addon.setStatus(request.status());
        }
    }

    private Addon load(Long id) {
        return addons.findById(id).orElseThrow(() -> new NotFoundException("Add-on", id));
    }
}
