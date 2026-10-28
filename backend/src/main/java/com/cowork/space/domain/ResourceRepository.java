package com.cowork.space.domain;

import com.cowork.shared.domain.ResourceType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceRepository {

    Optional<Resource> findById(UUID id);

    /** Đọc và khóa ghi hàng của vị trí đến hết giao dịch (SELECT ... FOR UPDATE). */
    Optional<Resource> findByIdForUpdate(UUID id);

    List<Resource> findBookable(ResourceType type, int minCapacity);

    List<Resource> findAll();

    Resource save(Resource resource);

    long count();
}
