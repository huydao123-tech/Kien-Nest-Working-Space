package com.cowork.space.domain;

import com.cowork.shared.domain.AggregateRoot;
import com.cowork.shared.domain.ConflictException;
import com.cowork.shared.domain.DomainException;
import com.cowork.shared.domain.ResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

/**
 * Vị trí cho thuê (ghế Hot Desk, bàn, pod, phòng họp...). Aggregate root của context Space.
 * Hàng của aggregate này được khóa bi quan khi đặt chỗ để tuần tự hóa các yêu cầu trên cùng vị trí.
 */
@Entity
@Table(name = "space_resource")
public class Resource extends AggregateRoot {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceType type;

    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceStatus status;

    @Version
    private Long version;

    protected Resource() {
    }

    public static Resource create(String code, String name, ResourceType type, int capacity) {
        if (capacity < 1) {
            throw new DomainException("INVALID_CAPACITY", "Sức chứa phải lớn hơn 0");
        }
        Resource resource = new Resource();
        resource.id = UUID.randomUUID();
        resource.code = code;
        resource.name = name;
        resource.type = type;
        resource.capacity = capacity;
        resource.status = ResourceStatus.ACTIVE;
        return resource;
    }

    /** BR-09, BR-14: chỉ vị trí đang khai thác và đủ sức chứa mới nhận đặt. */
    public void ensureCanHost(int partySize) {
        if (status != ResourceStatus.ACTIVE) {
            throw new ConflictException("RESOURCE_NOT_BOOKABLE", "Vị trí " + code + " đang " + status);
        }
        if (partySize > capacity) {
            throw new DomainException("CAPACITY_EXCEEDED", "Vị trí " + code + " chỉ chứa tối đa " + capacity + " người");
        }
    }

    public void markMaintenance() {
        if (status == ResourceStatus.INACTIVE) {
            throw new DomainException("RESOURCE_INACTIVE", "Vị trí đã ngưng khai thác");
        }
        status = ResourceStatus.MAINTENANCE;
    }

    public void reactivate() {
        status = ResourceStatus.ACTIVE;
    }

    public UUID id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public ResourceType type() {
        return type;
    }

    public int capacity() {
        return capacity;
    }

    public ResourceStatus status() {
        return status;
    }
}
