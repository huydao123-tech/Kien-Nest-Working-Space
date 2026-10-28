package com.cowork.space.infrastructure;

import com.cowork.shared.domain.ResourceType;
import com.cowork.space.domain.Resource;
import com.cowork.space.domain.ResourceRepository;
import com.cowork.space.domain.ResourceStatus;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaResourceRepository implements ResourceRepository {

    private final SpringDataResourceRepository jpa;

    JpaResourceRepository(SpringDataResourceRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Resource> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Resource> findByIdForUpdate(UUID id) {
        return jpa.lockById(id);
    }

    @Override
    public List<Resource> findBookable(ResourceType type, int minCapacity) {
        return jpa.findByTypeAndStatusAndCapacityGreaterThanEqualOrderByCode(type, ResourceStatus.ACTIVE, minCapacity);
    }

    @Override
    public List<Resource> findAll() {
        return jpa.findAll(Sort.by("code"));
    }

    @Override
    public Resource save(Resource resource) {
        return jpa.save(resource);
    }

    @Override
    public long count() {
        return jpa.count();
    }
}
