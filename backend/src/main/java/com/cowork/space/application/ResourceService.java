package com.cowork.space.application;

import com.cowork.shared.domain.NotFoundException;
import com.cowork.space.domain.Resource;
import com.cowork.space.domain.ResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ResourceService {

    private final ResourceRepository resources;

    public ResourceService(ResourceRepository resources) {
        this.resources = resources;
    }

    @Transactional(readOnly = true)
    public List<Resource> list() {
        return resources.findAll();
    }

    /** UC17: chặn đặt mới trên vị trí gặp sự cố. Sắp xếp lại booking tương lai chưa nằm trong phạm vi bản này. */
    public Resource markMaintenance(UUID id) {
        Resource resource = load(id);
        resource.markMaintenance();
        return resources.save(resource);
    }

    public Resource reactivate(UUID id) {
        Resource resource = load(id);
        resource.reactivate();
        return resources.save(resource);
    }

    private Resource load(UUID id) {
        return resources.findById(id).orElseThrow(() -> new NotFoundException("Vị trí", id));
    }
}
