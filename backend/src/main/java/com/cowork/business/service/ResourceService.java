package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.Resource;
import com.cowork.dataaccess.entity.ResourceStatus;
import com.cowork.dataaccess.repository.ResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Vị trí cho thuê và trạng thái khai thác. */
@Service
@Transactional
public class ResourceService {

    private final ResourceRepository resources;

    public ResourceService(ResourceRepository resources) {
        this.resources = resources;
    }

    @Transactional(readOnly = true)
    public List<Resource> list() {
        return resources.findAllByOrderByCodeAsc();
    }

    /** BR-09, BR-14: chỉ vị trí đang khai thác và đủ sức chứa mới nhận đặt. */
    public void requireBookable(Resource resource, int partySize) {
        if (resource.getStatus() != ResourceStatus.ACTIVE) {
            throw new ConflictException("RESOURCE_NOT_BOOKABLE", "Vị trí " + resource.getCode() + " đang " + resource.getStatus());
        }
        if (partySize > resource.getCapacity()) {
            throw new DomainException("CAPACITY_EXCEEDED",
                    "Vị trí " + resource.getCode() + " chỉ chứa tối đa " + resource.getCapacity() + " người");
        }
    }

    /** UC17: chặn đặt mới trên vị trí gặp sự cố. Sắp xếp lại booking tương lai chưa nằm trong phạm vi bản này. */
    public Resource markMaintenance(UUID id) {
        Resource resource = load(id);
        if (resource.getStatus() == ResourceStatus.INACTIVE) {
            throw new DomainException("RESOURCE_INACTIVE", "Vị trí đã ngưng khai thác");
        }
        resource.setStatus(ResourceStatus.MAINTENANCE);
        return resources.save(resource);
    }

    public Resource reactivate(UUID id) {
        Resource resource = load(id);
        resource.setStatus(ResourceStatus.ACTIVE);
        return resources.save(resource);
    }

    private Resource load(UUID id) {
        return resources.findById(id).orElseThrow(() -> new NotFoundException("Vị trí", id));
    }
}
