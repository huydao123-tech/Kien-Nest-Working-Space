package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.Booking;
import com.cowork.dataaccess.entity.SpaceResource;
import com.cowork.dataaccess.enums.BookingStatus;
import com.cowork.dataaccess.enums.ResourceStatus;
import com.cowork.dataaccess.repository.BookingRepository;
import com.cowork.dataaccess.repository.SpaceResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Vị trí cho thuê và trạng thái khai thác. */
@Service
@Transactional
public class ResourceService {

    private final SpaceResourceRepository resources;
    private final BookingRepository bookings;
    private final NotificationService notifications;
    private final Clock clock;

    public ResourceService(SpaceResourceRepository resources, BookingRepository bookings, NotificationService notifications,
                           Clock clock) {
        this.resources = resources;
        this.bookings = bookings;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SpaceResource> list() {
        return resources.findAllByOrderByCodeAsc();
    }

    /** BR-09, BR-14: chỉ vị trí đang khai thác và đủ sức chứa mới nhận đặt. */
    public void requireBookable(SpaceResource resource, int partySize) {
        if (resource.getStatus() != ResourceStatus.ACTIVE) {
            throw new ConflictException("RESOURCE_NOT_BOOKABLE", "Vị trí " + resource.getCode() + " đang " + resource.getStatus());
        }
        if (partySize > resource.getCapacity()) {
            throw new DomainException("CAPACITY_EXCEEDED",
                    "Vị trí " + resource.getCode() + " chỉ chứa tối đa " + resource.getCapacity() + " người");
        }
    }

    /**
     * UC17: chặn đặt mới trên vị trí gặp sự cố, rồi báo cho khách của các đơn đã thanh toán (CONFIRMED) chưa kết thúc
     * trên vị trí này (FR-OPS-07, FR-NOTI-04). Vị trí đã ở bảo trì thì giữ nguyên, không báo lại.
     * Sắp xếp lại / hoàn tiền cho các đơn đó chưa nằm trong phạm vi bản này.
     */
    public SpaceResource markMaintenance(Long id) {
        SpaceResource resource = load(id);
        if (resource.getStatus() == ResourceStatus.INACTIVE) {
            throw new DomainException("RESOURCE_INACTIVE", "Vị trí đã ngưng khai thác");
        }
        if (resource.getStatus() == ResourceStatus.MAINTENANCE) {
            return resource;
        }
        resource.setStatus(ResourceStatus.MAINTENANCE);
        SpaceResource saved = resources.save(resource);
        List<Booking> affected = bookings.findByResourceIdAndStatusAndPeriodEndAtAfterOrderByPeriodStartAtAsc(
                id, BookingStatus.CONFIRMED, LocalDateTime.now(clock));
        affected.forEach(notifications::resourceMaintenance);
        return saved;
    }

    public SpaceResource reactivate(Long id) {
        SpaceResource resource = load(id);
        resource.setStatus(ResourceStatus.ACTIVE);
        return resources.save(resource);
    }

    private SpaceResource load(Long id) {
        return resources.findById(id).orElseThrow(() -> new NotFoundException("Vị trí", id));
    }
}
