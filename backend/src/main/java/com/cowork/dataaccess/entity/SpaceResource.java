package com.cowork.dataaccess.entity;

import com.cowork.dataaccess.enums.ResourceStatus;
import com.cowork.dataaccess.enums.ResourceTypeCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.ArrayList;
import java.util.List;

/**
 * Vị trí cho thuê (ghế Hot Desk, bàn, pod, phòng họp...).
 * Hàng của bảng này được khóa bi quan khi đặt chỗ để tuần tự hóa các yêu cầu trên cùng vị trí.
 */
@Entity
@Table(name = "space_resource")
public class SpaceResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false)
    private String name;

    /** Bảng loại không gian rất nhỏ và hầu như luôn cần tới (tính giá), nên nạp kèm luôn (EAGER, mặc định của @ManyToOne). */
    @ManyToOne(optional = false)
    @JoinColumn(name = "resource_type_id", nullable = false)
    private ResourceType resourceType;

    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    /** Tọa độ tâm vị trí trên ảnh sơ đồ của khu vực, tính theo % chiều rộng / chiều cao ảnh (0–100). Rỗng = chưa đặt lên sơ đồ. */
    @Column(name = "pos_x")
    private Integer posX;

    @Column(name = "pos_y")
    private Integer posY;

    @Version
    private Long version;

    @OneToMany(mappedBy = "resource", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResourceAmenity> amenities = new ArrayList<>();

    protected SpaceResource() {
    }

    public SpaceResource(String code, String name, ResourceType resourceType, int capacity) {
        this.code = code;
        this.name = name;
        this.resourceType = resourceType;
        this.capacity = capacity;
        this.status = ResourceStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    /** Mã loại không gian, dùng cho tính giá và lọc chỗ trống. */
    public ResourceTypeCode getType() {
        return resourceType.getCode();
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public ResourceStatus getStatus() {
        return status;
    }

    public void setStatus(ResourceStatus status) {
        this.status = status;
    }

    public Zone getZone() {
        return zone;
    }

    public void setZone(Zone zone) {
        this.zone = zone;
    }

    public Integer getPosX() {
        return posX;
    }

    public void setPosX(Integer posX) {
        this.posX = posX;
    }

    public Integer getPosY() {
        return posY;
    }

    public void setPosY(Integer posY) {
        this.posY = posY;
    }

    public List<ResourceAmenity> getAmenities() {
        return amenities;
    }

    /** Thêm con và gán ngược về cha để hai phía quan hệ luôn khớp nhau. */
    public void addAmenity(ResourceAmenity amenity) {
        amenity.setResource(this);
        amenities.add(amenity);
    }
}
